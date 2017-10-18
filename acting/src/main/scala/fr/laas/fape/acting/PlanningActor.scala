package fr.laas.fape.acting

import akka.actor.FSM
import fr.laas.fape.acting.PlanningActor._
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.anml.model.concrete.GlobalRef
import fr.laas.fape.constraints.stnu.InconsistentTemporalNetwork
import fr.laas.fape.constraints.stnu.dispatching.DispatchableNetwork
import fr.laas.fape.planning.Planning
import fr.laas.fape.planning.core.planning.planner.Planner.EPlanState
import fr.laas.fape.planning.core.planning.planner.{Planner, PlanningOptions}
import fr.laas.fape.planning.core.planning.search.flaws.finders.NeededObservationsFinder
import fr.laas.fape.planning.core.planning.states.{Printer, State}
import fr.laas.fape.planning.core.planning.states.{State => PPlan}
import fr.laas.fape.planning.exceptions.PlanningInterruptedException
import fr.laas.fape.planning.util.TinyLogger

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.collection.mutable

object PlanningActor {
  def time() = System.currentTimeMillis()
  val repairTime = 10000
  sealed trait MState
  case object MIdle extends MState
  case object MPlanning extends MState
//  sealed trait Data


  sealed trait PlannerMessage
  object GetPlan extends PlannerMessage
  case class GetPlan(state: State, forHowLong: FiniteDuration, reqID: Int)
  case class TryRepair(state: State, forHowLong: FiniteDuration, numPlanReq: Int) extends PlannerMessage
  case class TryReplan(state: State, forHowLong: FiniteDuration, numPlanReq: Int) extends PlannerMessage
  case object RepairFailed extends PlannerMessage
  case object ReplanFailed extends PlannerMessage
  case class PlanFound(state: State, dispatcher: DispatchableNetwork[GlobalRef], numPlanReq: Int) extends PlannerMessage
  case class NoPlanExists(reqID: Int) extends PlannerMessage
  case class PlanningTimedOut(reqID: Int) extends PlannerMessage
}

class PlanningActor extends FSM[MState, Option[Planner]] with MessageLogger {

  val manager = context.actorSelection("..")

  val planners = mutable.Map[Int,Planner]()

  startWith(MIdle, None)

  when(MIdle) {
    case Event(GetPlan(initPlan, duration, reqID), _) =>
      launchPlanningProcess(initPlan, time()+duration.toMillis, reqID)
      goto(MPlanning)
  }

  when(MPlanning) {
    case Event(GetPlan(initPlan, duration, reqID), _) =>
      planners(reqID-1).stopPlanning = true
      planners -= reqID
      launchPlanningProcess(initPlan, time()+duration.toMillis, reqID)
      stay()
  }

  def launchPlanningProcess(initPlan: PPlan, deadline: Long, reqID: Int, checkDC : Boolean = false): Unit = {
    val options = Utils.options
    if(checkDC)
      options.flawFinders.add(new NeededObservationsFinder)
    val planner = new Planner(initPlan, options)
    planners += ((reqID, planner))
    Future {
      try {
        log.info("Starting Search")
        println(Printer.timelines(initPlan))
        val planningStatTime = System.currentTimeMillis()
        val solution = planner.search(deadline+10000)
        val planningTime = System.currentTimeMillis() - planningStatTime
        log.info(s"Planning finished in $planningTime ms")
        if (solution != null) {
          try {
            val dispatcherBuiltStartTime = System.currentTimeMillis()
            val dispatcher = DispatchableNetwork.getDispatchableNetwork(solution.csp.stn, solution.csp.stn.timepoints.asScala.toSet.asJava)
            val dispatcherBuildTime = System.currentTimeMillis() - dispatcherBuiltStartTime
            log.info(s"Built dispatcher in $dispatcherBuildTime ms")
            manager ! PlanFound(solution, dispatcher, reqID)
            println(Printer.actionsInState(solution))
            println(Printer.timelines(solution))
          } catch {
            case e:InconsistentTemporalNetwork =>
              assert(!checkDC, "Temporal inconsistency while building dispatcher even though activated DC checking")
              log.info("Found plan is not dynamically controllable. Restarting search with active dynamic controllability checking")
              initPlan.pl = null // remove the planner from this state
              launchPlanningProcess(initPlan, deadline, reqID, checkDC = true)
          }
        } else if(planner.planState == EPlanState.TIMEOUT) {
          manager ! PlanningTimedOut(reqID)
        } else {
          manager ! NoPlanExists(reqID)
        }
      } catch {
        case x: PlanningInterruptedException =>
          log.info(s"Planning interrupted ($reqID)")
        case e =>
          e.printStackTrace() // exceptions in futures disappear, we need to print them explicitly
          manager ! "Planner Crashed"
      }
    }
  }
}

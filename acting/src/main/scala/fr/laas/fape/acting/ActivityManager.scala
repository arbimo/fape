package fr.laas.fape.acting

import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy._
import akka.actor._
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages.{ExecutionRequest, TimepointActive, TimepointExecuted}
import fr.laas.fape.anml.model.AnmlProblem
import fr.laas.fape.anml.model.concrete.{Action, TPRef}
import fr.laas.fape.constraints.stnu.Controllability
import fr.laas.fape.constraints.stnu.dispatching.DispatchableNetwork
import fr.laas.fape.planning.core.planning.planner.Planner
import fr.laas.fape.planning.core.planning.states.modification.PartialPlanModification
import fr.laas.fape.planning.core.planning.states.{Printer, PartialPlan => PPlan}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.collection.mutable

case class WholeProblem(anmlFile: String)

object ActivityManager {
  trait MState
  case object MIdle extends MState
  case object MDispatching extends MState
  case object MWaitingForPlan extends MState

  trait MData
  case object MNothing extends MData
  class FullPlan(val plan: PPlan) extends MData {
    lazy val actionStarts : Map[TPRef,Action] = plan.getAllActions.asScala.map(a => (a.start, a)).toMap
    lazy val actionEnds : Map[TPRef,Action] = plan.getAllActions.asScala.map(a => (a.end, a)).toMap

    // maps every dispatchable timepoint inside an action to its containing action
    lazy val actionsInternalTimepoints : Map[TPRef,Action] = plan.getAllActions.asScala
      .flatMap(action => action.usedVariables.collect { case tp:TPRef if tp.genre.isDispatchable => (tp, action) })
      .filter(tpActPair => tpActPair._1 != tpActPair._2.start && tpActPair._1 != tpActPair._2.end)
      .toMap
  }

  case class SetGoal(goal: String)

  object Tick
}
import fr.laas.fape.acting.ActivityManager._

class ActivityManager extends FSM[MState, MData] with MessageLogger {
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ActorKilledException      => Restart
      case e                            => Restart
    }

  val baseDomainFile = "/home/abitmonn/working/robot.anml"

  import context.dispatcher
  import scala.concurrent.ExecutionContext.Implicits.global
  context.system.scheduler.schedule(0.seconds, 0.01.seconds, self, Tick)

  val planner = context.actorOf(Props[fr.laas.fape.acting.PlanningActor], name = "planner")
  val dispatcher = context.actorSelection("../dispatcher")

  val goals = new ArrayBuffer[PartialPlanModification]()
  val executed = mutable.Map[TPRef, Int]()
  val notifiedActive = mutable.Set[TPRef]()

  private var currentReqID = -1
  def getReqID() = { currentReqID+=1 ; currentReqID }

  startWith(MIdle, MNothing)

  when(MIdle) {
    case Event(goal:PartialPlanModification, _) =>
      goals += goal
      val pplan = getInitialPartialPlan
      planner ! PlanningActor.GetPlan(pplan, FiniteDuration(10, TimeUnit.SECONDS), getReqID())
      goto(MWaitingForPlan)

    case Event(Tick, _) => stay()
  }

  when(MWaitingForPlan) {
    case Event(goal:PartialPlanModification, _) =>
      log.info("Canceling current planning request to integrate new goal")
      goals += goal
      val pplan = getInitialPartialPlan
      planner ! PlanningActor.GetPlan(pplan, FiniteDuration(10, TimeUnit.SECONDS), getReqID())
      goto(MWaitingForPlan)

    case Event(PlanningActor.PlanFound(sol, reqID), _) =>
      if(reqID < currentReqID)
        stay()
      else {
        if(!executed.contains(sol.pb.start))
          executed += ((sol.pb.start, 0))
        goto(MDispatching) using new FullPlan(sol)
      }
    case Event(Tick, _) => stay()
  }

  when(MDispatching) {
    case Event(Tick, x: FullPlan) => // there should be no pending goals while dispatching

      val network = DispatchableNetwork.getDispatchableNetwork(x.plan.csp.stn, x.plan.csp.stn.timepoints.asScala.toSet.asJava)
      for(tp <- executed.keys) {
        if(!network.isExecuted(tp))
          network.setExecuted(tp, executed(tp))
      }
      val t = Clock.time()
      val executables = network.getExecutables(t).asScala
      for (tp <- executables) {
        if (x.actionStarts.contains(tp)) {
          log.info(s"[$t] Starting action: ${Printer.action(x.plan, x.actionStarts(tp))}")
          executed += ((tp, Clock.time()))
          dispatcher ! new ExecutionRequest(x.actionStarts(tp), x.plan, self)
        } else if(x.actionsInternalTimepoints.contains(tp)) {
          if(!notifiedActive.contains(tp)) {
            log.info(s"[$t] Notifying of active timepoint")
            notifiedActive.add(tp)
            dispatcher ! (x.actionsInternalTimepoints(tp), TimepointActive(tp, network.stn.getLatestTime(tp)))
          }
        } else if(!x.actionEnds.contains(tp)) {
          network.setExecuted(tp, t)
          executed += ((tp, t))
        }
      }
      network.stn.contingentLinks.map(_.dst)
        .filterNot(notifiedActive.contains)
        .filter(tp => network.stn.getEarliestTime(tp) <= t)
        .collect({
          case tp if x.actionEnds.contains(tp) => (tp, x.actionEnds(tp))
          case tp if x.actionsInternalTimepoints.contains(tp) => (tp, x.actionsInternalTimepoints(tp))
        })
        .foreach { case (tp, action) =>
          notifiedActive.add(tp)
          dispatcher ! (action, TimepointActive(tp, network.stn.getLatestTime(tp)))
        }
      stay()
  }

  whenUnhandled {
    case Event(TimepointExecuted(tp, time), _) =>
      executed += ((tp, time))
      stay()
  }

  def getInitialPartialPlan = {
    val pplan = new PPlan(Utils.getProblem, Controllability.PSEUDO_CONTROLLABILITY)
    for(g <- goals)
      pplan.apply(g, false)
    pplan
  }


  private def newProblem = new AnmlProblem()
}
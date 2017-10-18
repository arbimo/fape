package fr.laas.fape.acting

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import akka.actor.SupervisorStrategy._
import akka.actor._
import fr.laas.fape.acting.PlanningActor.{GetPlan, NoPlanExists, PlanFound}
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages.{ExecutionRequest, Observation, TimepointActive, TimepointExecuted}
import fr.laas.fape.anml.model.concrete.statements.Assignment
import fr.laas.fape.anml.model.{AnmlProblem, ParameterizedStateVariable}
import fr.laas.fape.anml.model.concrete._
import fr.laas.fape.constraints.stnu.{Controllability, InconsistentTemporalNetwork, STNU}
import fr.laas.fape.constraints.stnu.dispatching.DispatchableNetwork
import fr.laas.fape.constraints.stnu.structurals.StnWithStructurals
import fr.laas.fape.gui.{ChartLine, RectElem, TextLabel, TimedCanvas}
import fr.laas.fape.planning.core.planning.planner.Planner
import fr.laas.fape.planning.core.planning.states.modification._
import fr.laas.fape.planning.core.planning.states.{Printer, State => PPlan}
import fr.laas.fape.planning.core.planning.timelines.{ChainComponent, Timeline}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.collection.mutable

object ActivityManager {
  trait MState
  case object MIdle extends MState
  case object MDispatching extends MState
  case object MWaitingForPlan extends MState

  trait MData
  case object MNothing extends MData
  case class MPlanner(planner: Planner) extends MData
  case class MPendingGoals(state: PPlan, pendingGoals: List[String]) extends MData

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
  context.system.scheduler.schedule(0.seconds, 0.1.seconds, self, Tick)

  val planner = context.actorOf(Props[fr.laas.fape.acting.PlanningActor], name = "planner")

  val actionDispatcher = context.actorSelection("../actor")
  val observer = context.actorSelection("../observer")

  val goals = new ArrayBuffer[StateModification]()
  def candidateForAbandoning = goals.toSet.subsets().toList.sortBy(_.size)
  val previouslyAbandonedGoals = mutable.Set[Set[StateModification]]()
  var currentlyAbandonedGoals = Set[StateModification]()

  val executed = mutable.Map[TPRef, Int]()
  val notifiedActive = mutable.Set[TPRef]()
  val observations = new ArrayBuffer[Observation]()

  val actionExtensions = mutable.Map[Action,mutable.ArrayBuffer[StateModification]]()

  private var currentReqID = -1
  def getReqID() = { currentReqID+=1 ; currentReqID }

  startWith(MIdle, MNothing)

  when(MIdle) {
    case Event(goal:StateModification, _) =>
      goals += goal
      val pplan = getInitialPartialPlan
      planner ! PlanningActor.GetPlan(pplan, FiniteDuration(10, TimeUnit.SECONDS), getReqID())
      goto(MWaitingForPlan)

    case Event(Tick, _) => stay()
  }

  when(MWaitingForPlan) {
    case Event(goal:StateModification, _) =>
      log.info("Canceling current planning request to integrate new goal")
      goals += goal
      val pplan = getInitialPartialPlan
      planner ! PlanningActor.GetPlan(pplan, FiniteDuration(10, TimeUnit.SECONDS), getReqID())
      goto(MWaitingForPlan)

    case Event(PlanningActor.PlanFound(sol, dispatcher, reqID), _) =>
      if(reqID < currentReqID)
        stay()
      else {
        if(!executed.contains(sol.pb.start))
          executed += ((sol.pb.start, 0))
        goto(MDispatching) using new FullPlan(sol, dispatcher)
      }

    case Event(NoPlanExists(reqID), _) =>
      if(reqID != currentReqID)
        stay()
      else {
        previouslyAbandonedGoals += currentlyAbandonedGoals
        // need to abandon goals
        candidateForAbandoning.find(g => !previouslyAbandonedGoals.contains(g)) match {
          case Some(g) =>
            currentlyAbandonedGoals = g
            val pplan = getInitialPartialPlan
            planner ! PlanningActor.GetPlan(pplan, FiniteDuration(10, TimeUnit.SECONDS), getReqID())
            stay()
          case None =>
            log.error("No goal left to abandon, internal model is inconsistent")
            System.exit(1)
            stay()
        }
      }
    case Event(Tick, _) => stay()
  }

  when(MDispatching) {
    case Event(ExtendAction(act, mod), x:FullPlan) =>
      actionExtensions.getOrElseUpdate(act, new ArrayBuffer()) += mod
      val plan = getInitialPlanWithExecutingActions(x.plan)
      planner ! GetPlan(plan, 10.seconds, getReqID())
      x.canStartNewActions = false
      stay()

    case Event(goal:StateModification, x:FullPlan) =>
      goals += goal
      currentlyAbandonedGoals = Set()
      previouslyAbandonedGoals.clear()
      // something new come up, we might be able to achieve abandoned goals
      val plan = getInitialPlanWithExecutingActions(x.plan)
      planner ! GetPlan(plan, 10.seconds, getReqID())
      x.canStartNewActions = false
      stay()

    case Event(obs:Observation, x:FullPlan) =>
      observations += obs
      val modified = mergeObservation(obs, x.plan)
      if(modified && currentlyAbandonedGoals.nonEmpty) {
        currentlyAbandonedGoals = Set()
        previouslyAbandonedGoals.clear()
        // something new come up, we might be able to achieve abandoned goals
        val plan = getInitialPlanWithExecutingActions(x.plan)
        planner ! GetPlan(plan, 10.seconds, getReqID())
        x.canStartNewActions = false
        stay()
      } else {
        stay()
      }

    case Event(NoPlanExists(reqID), x:FullPlan) =>
      if(reqID != currentReqID)
        stay()
      else {
        def parents(a: Action) : List[Action] = {
          val parent = x.plan.taskNet.getContainingAction(a)
          if(parent != null) parent :: parents(parent)
          else Nil
        }
        val executingTasks = x.plan.getAllActions.asScala
          .filter(a => executed.contains(a.start))
          .flatMap(a => a :: parents(a))
          .map(a => x.plan.taskNet.getRefinedTask(a)).filter(_ != null)
          .toSet[Object]
        previouslyAbandonedGoals += currentlyAbandonedGoals
        // need to abandon goals
        candidateForAbandoning.find(g => !previouslyAbandonedGoals.contains(g) && g.forall(p => p.involvedObjects().asScala.toSet.intersect(executingTasks).isEmpty)) match {
          case Some(g) =>
            currentlyAbandonedGoals = g
            val pplan = getInitialPlanWithExecutingActions(x.plan)
            planner ! GetPlan(pplan, FiniteDuration(10, TimeUnit.SECONDS), getReqID())
            stay()
          case None =>
            log.error("No goal left to abandon, internal model is inconsistent")
//            System.exit(1)
            stay()
        }
      }

    case Event(PlanFound(sol, dispatcher, reqID), current:FullPlan) =>
      if(reqID != currentReqID)
        stay()
      else {
        stay() using new FullPlan(sol, dispatcher)
      }

    case Event(Tick, x: FullPlan) => // there should be no pending goals while dispatching
      for(tp <- executed.keys.toList.sortBy(executed(_))) {
        if(!x.dispatcher.isExecuted(tp)) {
          if (x.actionStarts.contains(tp))
            log.info(s"set exec start $tp ${x.actionStarts(tp)} ${executed(tp)}")
          else if (x.actionEnds.contains(tp))
            log.info(s"set exec end $tp ${x.actionEnds(tp)} ${executed(tp)}")
          else if (x.actionsInternalTimepoints.contains(tp))
            log.info(s"set exec inter $tp ${x.actionsInternalTimepoints(tp)} ${executed(tp)}")
          else
            log.info(s"set exec: $tp ${executed(tp)}")
          x.dispatcher.setExecuted(tp, executed(tp))
        }
      }
      val t = Clock.time()
      val executables = x.dispatcher.getExecutables(t).asScala
      for (tp <- executables if tp != x.plan.pb.end) {
        assert(!executed.contains(tp))
        assert(!x.dispatcher.isExecuted(tp))
        if (x.actionStarts.contains(tp)) {
          if(x.canStartNewActions) {
            log.info(s"[$t] Starting action: ${Printer.action(x.plan, x.actionStarts(tp))}")
            executed += ((tp, Clock.time()))
            actionDispatcher ! new ExecutionRequest(x.actionStarts(tp), x.plan, self)
          }
        } else if(x.actionsInternalTimepoints.contains(tp) || x.actionEnds.contains(tp)) {
          if(!notifiedActive.contains(tp)) {
            log.info(s"[$t] Notifying of active timepoint")
            notifiedActive.add(tp)
            val container = x.actionEnds.get(tp) match {
              case Some(act) => act
              case None => x.actionsInternalTimepoints(tp)
            }
            actionDispatcher ! (container.name, TimepointActive(tp))
          }
        } else if(!x.actionEnds.contains(tp)) {
          x.dispatcher.setExecuted(tp, t)
          executed += ((tp, t))
        }
      }
      x.print(executed.keySet.toSet, t)
      stay()
  }

  whenUnhandled {
    case Event(TimepointExecuted(tp, time), _) =>
      executed += ((tp, time))
      stay()

    case Event(obs:Observation, _) =>
      observations += obs
      stay()

    case Event(Tick, _) =>
      stay()
  }

  def getInitialPlanWithExecutingActions(currentPlan: PPlan) = {
    def parents(a: Action) : List[Action] = {
      val parent = currentPlan.taskNet.getContainingAction(a)
      if(parent != null) parent :: parents(parent)
      else Nil
    }
    val pplan = new PPlan(Utils.getProblem, Controllability.PSEUDO_CONTROLLABILITY)
    for(g <- goals if !currentlyAbandonedGoals.contains(g))
      pplan.apply(g, false)

    val executingActions = currentPlan.getAllActions.asScala
      .filter(a => executed.contains(a.start))
      .flatMap(a => a :: parents(a))
      .toSet

    val allMods = currentPlan.getStateModifications.asScala
      .collect {
        case mod:SequenceOfStateModifications => mod.modifications.asScala
        case mod => List(mod)
      }.flatten
    val toKeep = allMods.collect {
      case x:ActionInsertion if executingActions.contains(x.action) =>
        if(actionExtensions.contains(x.action)) new ComposedActionInsertion(x, actionExtensions(x.action))
        else x
      case x:TaskRefinement if executingActions.contains(x.refiningAction) => x
      case x: ComposedActionInsertion =>
        assert(actionExtensions.contains(x.actionInsertion.action))
        assert(x.extensions.forall(ext => actionExtensions(x.actionInsertion.action).contains(ext)))
        new ComposedActionInsertion(x.actionInsertion, actionExtensions(x.actionInsertion.action))
    }
    toKeep.foreach(mod => pplan.apply(mod, false))

    // add execution constraints
    for((tp,time) <- executed.toList.sortBy(_._2)) {
      pplan.csp.stn.asInstanceOf[StnWithStructurals[GlobalRef]].forceExecutionTime(tp, time)
    }
    for(obs <- observations)
      println(mergeObservation(obs, pplan))
    // make sure all additional actions cannot start in the past
    pplan.setEarliestExecution(Clock.time())
    pplan
  }

  def getInitialPartialPlan = {
    val pplan = new PPlan(Utils.getProblem, Controllability.PSEUDO_CONTROLLABILITY)
    for(g <- goals if !currentlyAbandonedGoals.contains(g))
      pplan.apply(g, false)
    pplan.setEarliestExecution(Clock.time())
    pplan
  }

  private def newProblem = new AnmlProblem()


  case class Interval(start: TPRef, end: TPRef, value: String, default: String)

  /** Merges an observation in the current state, returns true if a new assertion was inserted */
  def mergeObservation(obs: Observation, plan: PPlan) :Boolean = {
    val Observation(sv, value, time) = obs
    val UNDEFINED = "_UNDEFINED_"
    val ANY = "_ANY_"
    def start(component: ChainComponent) = {
      assert(component.change)
      component.getFirst.start
    }
    def end(component: ChainComponent) = {
      assert(component.change)
      component.getFirst.end
    }
    def est(tp: TPRef) = plan.csp.stn.getEarliestTime(tp)
    def lst(tp: TPRef) = plan.csp.stn.getLatestTime(tp)

    val timelines = plan.tdb.getTimelinesList.asScala
      .filter(tl => plan.unifiable(sv, tl.stateVariable))
      .filter(tl => !tl.hasSinglePersistence)

    val components = timelines.map(tl => tl.chain.toList
      .filter(cc => cc.change || cc == tl.getLast)
      .takeWhile(cc => est(cc.getConsumeTimePoint) == lst(cc.getConsumeTimePoint))) // TODO should take into account contingents
      .filter(_.nonEmpty)
      .sortBy(ccs => est(ccs.head.getConsumeTimePoint))
      .flatten

    val intervals = ArrayBuffer[Interval]()
    intervals += Interval(plan.pb.start, if(components.isEmpty) plan.pb.end else components.head.getFirst.start, ANY, UNDEFINED)
    for((cc, i) <- components.zipWithIndex) {
      if(cc.change) {
        if(cc.getFirst.needsSupport) {
          // change assertion
          intervals += Interval(start(cc), end(cc), UNDEFINED, UNDEFINED)
          val endOfOutCausalLink =
            if(i+1 < components.size && components(i+1).change && components(i+1).getFirst.needsSupport)
              Some(components(i+1).getFirst.start)
            else if (i+2 < components.size && !components(i+1).change && components(i+2).getFirst.needsSupport)
              Some(components(i+2).getFirst.start)
            else if(i+1 < components.size && !components(i+1).change)
              Some(components(i+1).getFirst.end)
            else
              None
          val supportValue = Utils.asString(cc.getSupportValue, plan)
          if(endOfOutCausalLink.nonEmpty)
            intervals += Interval(end(cc), endOfOutCausalLink.get, supportValue, supportValue)
        } else { // assignment
          if(i > 0) {
            // record previous one
            val start = components(i - 1).getFirst.end
            val end = cc.getConsumeTimePoint
            val defaultVal = Utils.asString(components(i-1).getSupportValue, plan)
            intervals += Interval(start, end, ANY, defaultVal)
          }
        }
      } else { // group of persistences
        assert(i > 0)
        val value = Utils.asString(cc.getFirst.endValue, plan)
        if(i+1 == components.size || !components(i+1).getFirst.needsSupport) {
          // no causal link after
          intervals += Interval(end(components(i-1)), cc.getFirst.end, value, value)
        } else {
          // causal link after
          intervals += Interval(end(components(i-1)), start(components(i+1)), value, value)
        }
      }
      if(i == components.size-1) { // last one, record persistence by default
        intervals += Interval(cc.getSupportTimePoint, plan.pb.end, ANY, Utils.asString(cc.getSupportValue, plan))
      }
    }
//    println(intervals.map(i => s"${est(i.start)} ${lst(i.end)} ${i.value} ${i.default}"))

    val temporallyFittingIntervals = intervals
      .dropWhile(it => lst(it.end) < time)
      .takeWhile(it => est(it.start) <= time)

    val candidateMerges = temporallyFittingIntervals
      .dropWhile(it => !Set(UNDEFINED, ANY, value).contains(it.value))
      .takeWhile(it => Set(UNDEFINED, ANY, value).contains(it.value))

    if(candidateMerges.isEmpty) {
      // error
      throw new InconsistentObservationException(obs)
    } else if(candidateMerges.forall(it => it.value == ANY && (it.default == UNDEFINED || it.default != value.instance))) {
      // need new assignment
      val chronicle = new Chronicle
      val assignment = new Assignment(sv, value, chronicle, plan.pb.refCounter)
      chronicle.addStatement(assignment)
      chronicle.addConstraint(new MinDelayConstraint(plan.pb.start, assignment.end, time))
      chronicle.addConstraint(new MinDelayConstraint(assignment.end, plan.pb.start, -time))
      chronicle.addConstraint(new MinDelayConstraint(candidateMerges.head.start, assignment.end, 0))
      chronicle.addConstraint(new MinDelayConstraint(assignment.start, assignment.end, 1))
      chronicle.addConstraint(new MinDelayConstraint(assignment.end, assignment.start, -1))

      //      chronicle.addConstraint(new MinDelayConstraint(assignment.end, candidateMerges.last.end, 0))
      try {
        val mod = new ChronicleInsertion(chronicle)
        plan.apply(mod, false)
      } catch {
        case e:InconsistentTemporalNetwork => println("arg")
          throw new InconsistentObservationException(obs)
      }
      return true
    } else {
      return false
    }
  }
}

class FullPlan(val plan: PPlan, val dispatcher: DispatchableNetwork[GlobalRef]) extends MData {
  var canStartNewActions = true

  lazy val actionStarts : Map[TPRef,Action] = plan.getAllActions.asScala.map(a => (a.start, a)).toMap
  lazy val actionEnds : Map[TPRef,Action] = plan.getAllActions.asScala.map(a => (a.end, a)).toMap

  // maps every dispatchable timepoint inside an action to its containing action
  lazy val actionsInternalTimepoints : Map[TPRef,Action] = plan.getAllActions.asScala
    .flatMap(action => action.usedVariables.collect { case tp:TPRef if tp.genre.isDispatchable => (tp, action) })
    .filter(tpActPair => tpActPair._1 != tpActPair._2.start && tpActPair._1 != tpActPair._2.end)
    .toMap

  val observationsQueue = mutable.Queue[Observation]()

  private var lastDraw : Long = 0

  def print(executed: Set[TPRef], currentTime: Int): Unit = {
    if(plan.getAllActions.size() == 0)
      return
    if(System.currentTimeMillis() - lastDraw < 100)
      return
    lastDraw = System.currentTimeMillis()
    val acts = plan.getAllActions.asScala
      .filter(a => a.start.genre.isDispatchable)
      .sortBy(a => plan.getEarliestStartTime(a.start))

    val lines = mutable.ArrayBuffer[ChartLine]()

    for (a <- acts) {
      val start = plan.getEarliestStartTime(a.start)
      val earliestEnd = plan.getEarliestStartTime(a.end)
      val name = Printer.action(plan, a)
      val label = new TextLabel(name, "action-name")
      if(executed.contains(a.end)) {
        lines += new ChartLine(label, new RectElem(start, earliestEnd - start, "successful"))
      } else { //if(executed.contains(a.start)) {
        val min = plan.csp.stn.getMinDelay(a.start, a.end)
        val max = plan.csp.stn.getMaxDelay(a.start, a.end)
        val earliestEnd = Math.max(start + min, currentTime)
        lines += new ChartLine(label,
          new RectElem(start, earliestEnd -start, "pending"),
          new RectElem(earliestEnd + 0.1f, max - min, "uncertain"))
      }
    }
    val canvas = new TimedCanvas(lines, Some(currentTime.toFloat))
    Utils.chartWindow.draw(canvas)
  }
}
package fr.laas.fape.acting.scenario.cleanup.action

import java.util

import akka.actor.FSM
import fr.laas.fape.acting.ActivityManager.Tick
import fr.laas.fape.acting.actors.patterns.{DelayedForwarder, MessageLogger}
import fr.laas.fape.acting.messages._
import fr.laas.fape.acting.scenario.cleanup.action.NavigateTo.{MData, MFailure}
import fr.laas.fape.acting.{Clock, ExtendAction, Utils}
import fr.laas.fape.anml.model.concrete._
import fr.laas.fape.planning.core.planning.states.State
import fr.laas.fape.planning.core.planning.states.modification.{ActionInsertion, ChronicleInsertion, StateModification}
import fr.laas.fape.ros.ROSUtils
import fr.laas.fape.ros.action.{GoToPick, MoveBaseClient, MoveBlind}
import fr.laas.fape.ros.database.Database
import fr.laas.fape.ros.exception.ActionFailure
import fr.laas.fape.ros.message.MessageFactory
import gtp_ros_msg.Pt

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._

class Expand(val taskName: String, table: String, action: Action) extends StateModification {
  val task = taskName match {
    case "GoLook" =>
      new Task("t-GoLook", List(Utils.getProblem.instance("PR2_ROBOT"), Utils.getProblem.instance(table)), None, RefCounter.getGlobalCounter)
    case "Transport" =>
      new Task("t-Transport", action.args, None, RefCounter.getGlobalCounter)
  }
  val chronicle =  new Chronicle
  chronicle.addTask(task)
  chronicle.addConstraint(new MinDelayConstraint(task.end, action.end, 1))
  val baseMod = new ChronicleInsertion(chronicle)
  override def apply(st: State, isFastForwarding: Boolean): Unit = {
    baseMod.apply(st, isFastForwarding)
  }

  override def involvedObjects(): util.Collection[AnyRef] = ???


}



/** Data is a list of tables, the first one being current looked for */
class SearchTransport(robot: String)  extends FSM[String,(Option[ExecutionRequest],List[String])] with MessageLogger with DelayedForwarder {

  case class IsOn(obj: String, table: String)

  import context.dispatcher
  context.system.scheduler.schedule(0.seconds, 0.05.seconds, self, "tick")

  context.actorSelection("../../observer") ! "subscribe"

  startWith("idle", (None,Nil))

  var currentCtg : TPRef = null
  val objectLocations = mutable.Map[String,String]()


  when("idle") {
    case Event(req:ExecutionRequest, _) =>
      currentCtg = req.action.context.getTimepoint("beforeEnd")
      val targets = Database.getTables.asScala.toList
        .filterNot(table => Database.getBool("seen/"+table, false))
        .sortBy(table => ROSUtils.dist(Database.getPoseOf("PR2_ROBOT"), Database.getPoseOf(table)))
      if(objectLocations.contains(req.parameters(1))) {
        log.info("Object, location known, refining to transport")
        req.caller ! TimepointExecuted(currentCtg, Clock.time())
        req.caller ! ExtendAction(req.action, new Expand("Transport", "", req.action))
        goto("transporting") using (Some(req), Nil)
      } else if(targets.isEmpty) {
        log.error("All tables were previously observed")
        req.caller ! TimepointExecuted(currentCtg, Clock.time())
        forwardLater(3.seconds, "stop-waiting-for-obs", self)(self)
        goto("all-explored") using(Some(req), Nil)
        stay()
      } else {
        log.info("Looking for table: "+targets.head)
        req.caller ! TimepointExecuted(currentCtg, Clock.time())
        req.caller ! ExtendAction(req.action, new Expand("GoLook", targets.head, req.action))
        goto("searching") using(Some(req), targets)
      }
  }

  when("searching") {
    case Event("tick", (Some(req), current :: pending)) =>
      if(Database.getBool("seen/"+current, false)) {
        if (pending.isEmpty) {
          log.info("Explored the entire environment")
          forwardLater(3.seconds, "stop-waiting-for-obs", self)(self)
          goto("all-explored") using(Some(req), Nil)
        } else {
          req.caller ! ExtendAction(req.action, new Expand("GoLook", pending.head, req.action))
          stay() using(Some(req), pending)
        }
      } else
        stay()

    case Event(IsOn(obj, table), (Some(req), _)) =>
      if(obj == req.parameters(1)) {
        log.info("Object, location known, refining to transport")
        req.caller ! ExtendAction(req.action, new Expand("Transport", "", req.action))
        goto("transporting")
      } else {
        stay()
      }
  }

  when("all-explored") {
    case Event(IsOn(obj, table), (Some(req), _)) =>
      if(obj == req.parameters(1)) {
        log.info("Object, location known, refining to transport")
        req.caller ! ExtendAction(req.action, new Expand("Transport", "", req.action))
        goto("transporting")
      } else {
        stay()
      }

    case Event("stop-waiting-for-obs", (Some(req), Nil)) =>
      log.error("No location was found for target object after exploring everything, aborting")
      req.caller ! Failed(req)
      goto("idle") using (None, Nil)
  }

  when("transporting") {
    case _ =>
      stay()
  }

  whenUnhandled {
    case Event(obs:Observation, _) =>
      if(obs.sv.func.name == "Object.loc") {
        val obj = obs.sv.arg(0).asInstanceOf[InstanceRef].instance
        val table = obs.value.instance
        if (!objectLocations.contains(obj) || objectLocations(obj) != table) {
          log.info(s"Found object $obj on $table")
          objectLocations += ((obj, table))
          self ! IsOn(obj, table)
        }
      }
      stay()

    case Event(observations:List[Observation], _) =>
      for(obs <- observations) {
        if (obs.sv.func.name == "Object.loc") {
          val obj = obs.sv.arg(0).asInstanceOf[InstanceRef].instance
          val table = obs.value.instance
          if (!objectLocations.contains(obj) || objectLocations(obj) != table) {
            log.info(s"Found object $obj on $table")
            objectLocations += ((obj, table))
            self ! IsOn(obj, table)
          }
        }
      }
      stay()

    case Event("stop-waiting-for-obs", _) => // we already got the observation, otherwise the message would have been catched
      stay()

    case Event("tick",_) =>
      stay()
  }
}

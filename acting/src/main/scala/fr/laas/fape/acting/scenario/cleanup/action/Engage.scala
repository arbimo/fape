package fr.laas.fape.acting.scenario.cleanup.action

import akka.actor.FSM
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages.{ExecutionRequest, Failed, TimepointExecuted}
import fr.laas.fape.acting.{Clock, Utils}
import fr.laas.fape.anml.model.concrete.MinDelayConstraint
import fr.laas.fape.ros.ROSUtils
import fr.laas.fape.ros.action.{GoToPick, MoveBaseClient, MoveBlind}
import fr.laas.fape.ros.database.Database
import fr.laas.fape.ros.exception.ActionFailure

class Engage(robot: String)  extends FSM[String,Option[ExecutionRequest]] with MessageLogger {
  startWith("idle", None)

  var isFirst = true

  when("idle") {
    case Event(req:ExecutionRequest, _) =>
      if(isFirst) {
        val g = Utils.buildTask("Transport", List("PR2_ROBOT", "GREY_TAPE", "TABLE"))
//        g.chronicle.addConstraint(new MinDelayConstraint(g.chronicle.tasks.get(0).end, req.plan.taskNet.getContainingAction(req.action).end, 1))
        context.actorSelection("../../manager") ! g
        isFirst = false
      }
      MoveBlind.cancelAllGoals()
      MoveBaseClient.cancelAllGoals()
      assert(req.name == "Engage")
      if(Database.getBool("engaged", false)) {
        log.error("Robot is already engaged, aborting")
        req.caller ! Failed(req)
      }
      Utils.Future {
        try {
          GoToPick.engage()
          Database.setBool("engaged", true)
          self ! "success"
        } catch {
          case e:ActionFailure =>
            log.error("Action failure")
            e.printStackTrace()
            self ! "failure"
        }
      }
      goto("active") using Some(req)
  }

  when("active") {
    case Event("success", Some(exe)) =>
      exe.caller ! TimepointExecuted(exe.action.end, Clock.time())
      goto("idle") using None

    case Event("failure", Some(exe)) =>
      exe.caller ! Failed(exe)
      goto("idle") using None

    case Event(req:ExecutionRequest, Some(previous)) =>
      MoveBlind.cancelAllGoals()
      MoveBaseClient.cancelAllGoals()
      self ! req // reconsider when in idle state
      goto("idle") using None
  }


}

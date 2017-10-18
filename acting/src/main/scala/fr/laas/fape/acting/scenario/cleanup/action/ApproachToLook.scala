package fr.laas.fape.acting.scenario.cleanup.action

import akka.actor.FSM
import fr.laas.fape.acting.{Clock, Utils}
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages.{AAction, ExecutionRequest, TimepointExecuted}
import fr.laas.fape.ros.ROSUtils
import fr.laas.fape.ros.action.{ApproachAngle, GoToPick, MoveBaseClient, MoveBlind}
import fr.laas.fape.ros.database.Database
import fr.laas.fape.ros.exception.ActionFailure
import fr.laas.fape.ros.message.MessageFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ApproachToLook(robot: String)  extends FSM[String,Option[ExecutionRequest]] with MessageLogger {

  startWith("idle", None)

  when("idle") {
    case Event(exe:ExecutionRequest, _) =>
      Utils.Future {
        try {
          if (Database.getBool("engaged", false))
            MoveBlind.moveBackward(0.5)
          val bot :: target :: _ = exe.parameters
          if(ROSUtils.dist(Database.getPoseOf(bot), Database.getPoseOf(target)) > 2) {
            log.info("To far, I will get closer.")
            log.info("Looking for approach angle")
            val approachAngle = GoToPick.getFeasibleApproach(bot, target, 2)
            log.info("Found approach angle, computing target pose")
            val navTarget = GoToPick.getManipulationPose(MessageFactory.getXYYawFromPose(Database.getPoseOf(target)), approachAngle, 1.9)
            val curPoint = Database.getPoseOf(bot).getPosition
            val angle = ROSUtils.angleTowards(curPoint.getX, curPoint.getY, navTarget.getX, navTarget.getY)
            log.info("Start moving")
            MoveBaseClient.sendGoTo(navTarget.getX, navTarget.getY, angle)
          } else {
            val targetAngle = ROSUtils.angleTowards(bot, target)
            val cur = MessageFactory.getXYYawFromPose(Database.getPoseOf(target)).getZ
            if((Math.abs(targetAngle - cur) % (2*Math.PI)) < Math.PI /2)
              MoveBlind.turnTowards(targetAngle)
          }
          println("finished")
          self ! "success"
        } catch {
          case e:ActionFailure =>
            self ! "failure"
        }
      }
      goto("active") using Some(exe)
  }

  when("active") {
    case Event("success", Some(exe)) =>
      exe.caller ! TimepointExecuted(exe.action.end, Clock.time())
      goto("idle") using None

    case Event("failure", Some(exe)) =>
      log.error("FAILURE")
      goto("idle") using None

    case Event(req:ExecutionRequest, Some(previous)) =>
      MoveBlind.cancelAllGoals()
      MoveBaseClient.cancelAllGoals()
      goto("idle") using Some(req)
  }


}

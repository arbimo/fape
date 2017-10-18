package fr.laas.fape.acting.actors.patterns

import akka.actor.Actor
import akka.event.Logging
import fr.laas.fape.acting.ActivityManager.Tick
import fr.laas.fape.acting.messages.Observation

trait MessageLogger extends Actor {

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
    msg match {
      case Tick =>
      case "tick" =>
      case _:Observation =>
      case _ => Logging(context.system, this).info(s"From: [${sender().path}] --> $msg")
    }
    super.aroundReceive(receive, msg)
  }
}

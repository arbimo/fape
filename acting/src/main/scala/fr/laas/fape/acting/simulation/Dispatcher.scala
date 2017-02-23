package fr.laas.fape.acting.simulation

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import fr.laas.fape.acting.Clock
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages._
import fr.laas.fape.anml.model.concrete.Action

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Dispatcher extends Actor with MessageLogger {
  val log = Logging(context.system, this)

  val handlers = mutable.Map[Action,ActorRef]()

  def receive = {
    case e:ExecutionRequest =>
      log.error(s"No actor for ${e.name}, simulating execution")
      val handler = context.actorOf(Props[SimulatedSkill], name = s"${e.name}_${e.action.id}")
      handler ! e
      handlers += ((e.action, handler))

    case (action: Action, data) =>
      handlers.get(action) match {
        case Some(handler) => handler ! data
        case None =>log.error(s"No previously recorded handler for $action")
      }

    case x => log.error(s"Unhandled: $x")
  }

}

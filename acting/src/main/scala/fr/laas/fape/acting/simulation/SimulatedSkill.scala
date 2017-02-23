package fr.laas.fape.acting.simulation

import akka.actor.FSM
import fr.laas.fape.acting.Clock
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages.{ExecutionRequest, TimepointActive, TimepointExecuted}
import fr.laas.fape.anml.model.concrete.TPRef

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SimulatedSkill extends FSM[String,Option[ExecutionRequest]] with MessageLogger {

  startWith("idle", None)

  when("idle") {
    case Event(exe:ExecutionRequest, _) =>
      goto("active") using Some(exe)
  }

  when("active") {
    case Event(TimepointActive(tp, deadline), Some(exe)) =>
      exe.caller ! TimepointExecuted(tp, Clock.time())
      stay()
  }
}


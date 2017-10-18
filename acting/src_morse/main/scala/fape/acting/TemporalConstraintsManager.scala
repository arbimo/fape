package fape.acting

import akka.actor.{Actor, ActorRef}
import akka.actor.Actor.Receive
import fape.acting.drawing.SVG
import fape.actors.patterns.MessageLogger

import fr.laas.fape.anml.model.concrete.ActRef
import fr.laas.fape.planning.core.execution.model.AtomicAction

case class Endable(act: ActRef)
case class SubscribeEndable(act: AtomicAction)

class TemporalConstraintsManager extends Actor with MessageLogger {

  var endableActionsSubscribers = Map[ActRef, List[ActorRef]]()

  override def receive: Receive = {
    case x =>
//    case p:Plan =>
//      for(act <- endableActionsSubscribers.keys ; if p.isEndable(act)) {
//        for(subscriber <- endableActionsSubscribers(act))
//          subscriber ! Endable(act)
//        endableActionsSubscribers = endableActionsSubscribers.updated(act, Nil)
//      }
//      SVG.printSvgToFile(p.getState, s"${Main.outDir}plan-${Time.now}.svg")
//
//    case SubscribeEndable(act) =>
//      endableActionsSubscribers = endableActionsSubscribers.updated(act.id, sender() :: endableActionsSubscribers.getOrElse(act.id, Nil))
  }
}

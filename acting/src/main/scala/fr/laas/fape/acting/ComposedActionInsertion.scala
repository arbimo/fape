package fr.laas.fape.acting

import java.util

import fr.laas.fape.anml.model.concrete.Action
import fr.laas.fape.planning.core.planning.states.State
import fr.laas.fape.planning.core.planning.states.modification.{ActionInsertion, StateModification}

import scala.collection.JavaConverters._

case class ExtendAction(action: Action, extension: StateModification)

class ComposedActionInsertion(val actionInsertion: ActionInsertion, val extensions: Seq[StateModification]) extends StateModification {
  override def apply(st: State, isFastForwarding: Boolean): Unit = {
    actionInsertion.apply(st, false)
    for(ext <- extensions)
      ext.apply(st, false)
  }

  override def involvedObjects(): util.Collection[AnyRef] =
    (actionInsertion :: extensions.toList).flatMap(_.involvedObjects().asScala.toSet).asJava
}

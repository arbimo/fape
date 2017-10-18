package fr.laas.fape.acting

import fr.laas.fape.acting.messages.Observation

/**
  * Created by abitmonn on 11/24/16.
  */
case class InconsistentObservationException(obs: Observation) extends Exception(obs.toString) {

}

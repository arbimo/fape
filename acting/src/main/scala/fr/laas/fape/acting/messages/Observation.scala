package fr.laas.fape.acting.messages

import fr.laas.fape.anml.model.ParameterizedStateVariable
import fr.laas.fape.anml.model.concrete.InstanceRef


case class Observation(sv: ParameterizedStateVariable, value: InstanceRef, time: Int) {

}

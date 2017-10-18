package fr.laas.fape.acting.scenario.cleanup

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import fr.laas.fape.acting.{Clock, Utils}
import fr.laas.fape.acting.actors.patterns.MessageLogger
import fr.laas.fape.acting.messages._
import fr.laas.fape.acting.scenario.cleanup.action.{ApproachToLook, LookAt, NavigateTo}
import fr.laas.fape.ros.ROSUtils
import fr.laas.fape.ros.database.Database

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class Observer extends Actor with MessageLogger {
  val log = Logging(context.system, this)

  val manager = context.actorSelection("../manager")

  import context.dispatcher
  context.system.scheduler.schedule(0.seconds, 1.seconds, self, "tick")

  val observations = ArrayBuffer[Observation]()
  val subscribers = ArrayBuffer[ActorRef]()

  def receive = {
    case "tick"  =>
      val objects = Database.getObjects.asScala.toSet
      val tables = objects.filter(_.contains("TABLE"))
      val tapes = objects.filter(_.contains("TAPE"))

      def pose(obj: String) = Database.getPoseOf(obj)

      def tableOf(obj: String) = tables.find(t => ROSUtils.dist(pose(obj), pose(t)) < 0.8)
      val isOn =  tapes
        .filter(tape => pose(tape).getPosition.getZ > 0.7 && pose(tape).getPosition.getZ <0.75)
        .map(tape => (tape,tableOf(tape)))
        .collect { case (tape, Some(table)) => (tape, table)}
        .toMap

      val seenTables = tables.filter(table => Database.getBool(s"seen/$table", false))
      val isOnSeen = isOn.toList.filter(entry => seenTables.contains(entry._2)).toMap
//      println(s"IsOn: $isOn")
//      println(s"IsOnSeen: $isOnSeen")

      isOnSeen.foreach(isOn => {
        val sv = Utils.getProblem.stateVariable("Object.loc", List(isOn._1))
        val value = Utils.getProblem.instance(isOn._2)
        val obs = Observation(sv, value, Clock.time())
        observations += obs
        subscribers.foreach(_ ! obs)
        manager ! obs
      })

    case "subscribe" =>
      sender() ! observations.toList
      subscribers += sender()

    case x => log.error(s"Unhandled: $x")
  }



}

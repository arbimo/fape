package fape.scenarios.morse

import java.io.File
import java.util.logging.{Level, Logger}

import akka.actor.{ActorSystem, Props}
import fr.laas.fape.acting.scenario.cleanup.Observer
import fr.laas.fape.acting.{ActivityManager, Clock, Utils}
import fr.laas.fape.anml.model.AnmlProblem
import fr.laas.fape.anml.model.concrete.{Chronicle, MinDelayConstraint}
import fr.laas.fape.anml.model.concrete.statements.Persistence
import fr.laas.fape.constraints.stnu.Controllability
import fr.laas.fape.planning.Planning
import fr.laas.fape.planning.core.planning.planner.{Planner, PlanningOptions}
import fr.laas.fape.planning.core.planning.states.modification.ChronicleInsertion
import fr.laas.fape.planning.core.planning.states.{Printer, State}
import fr.laas.fape.planning.util.TinyLogger
import fr.laas.fape.ros.GTP
import fr.laas.fape.ros.action._
import fr.laas.fape.ros.database.Database
import fr.laas.fape.ros.sensing.CourseCorrection
import geometry_msgs.Point
import org.apache.commons.logging.LogFactory


object Main extends App {
  Utils.setProblem("/home/abitmonn/working/robot.anml")
  Planning.quiet = false
  Planning.verbose = false
  TinyLogger.logging = false

  Logger.getGlobal.setLevel(Level.OFF)
  Logger.getLogger("ros").setLevel(Level.OFF)
  LogFactory.getLog("")
//  Plan.makeDispatchable = true
//  Plan.showChart = true
//  APlanner.logging = false
//  APlanner.debugging = false

  // start database
  Database.initialize()

  //   initialize all ros clients (mainly to be done of annoying execution messages)
  MoveBaseClient.getInstance()
  MoveBlind.getInstance()
  MoveTorsoActionServer.getInstance()
  MoveLeftArm.getInstance()
  MoveRightArm.getInstance()
  GripperOperator.getInstance()
  MoveArmToQ.getInstance()
  LootAt.getInstance()
  GTP.getInstance()

  CourseCorrection.spin()
  MoveTorsoActionServer.moveTorso(0.3)
//  MoveBlind.moveBackward(1) ; Database.setBool("engaged", false)
  val iniLoc = Database.getPoseOf("PR2_ROBOT").getPosition
  if(iniLoc.getX < 0.3 && iniLoc.getY < 0.3)
    MoveBaseClient.sendGoTo(1,.5,Math.PI/4) // necessary since Move3D stocks a lot of stuff in (0,0)
  MoveArmToQ.moveLeftToNavigationPose()
  MoveArmToQ.moveRightToNavigationPose()
//  MoveBlind.moveForward(0.5)
//  MoveBlind.turnTowards(Math.PI/3)
  val system = ActorSystem("fape")

  val manager = system.actorOf(Props[ActivityManager], name = "manager")

  val actor = system.actorOf(Props[Dispatcher], name = "actor")
  val observer = system.actorOf(Props[Observer], name = "observer")



  val goal = Utils.buildTask("GoLook", List("PR2_ROBOT", "TABLE_1")) //Utils.buildGoal("Table.observed", List("TABLE_0"), "true", 3)
  val goal2 = Utils.buildTask("GoLook", List("PR2_ROBOT", "TABLE_0")) // Utils.buildGoal("Table.observed", List("TABLE"), "true", 3)
//  manager ! goal
//  manager ! goal2
  //  manager ! goal2
  val pickTask = Utils.buildTask("GoPick", List("PR2_ROBOT","GREY_TAPE"), Clock.time() +100000)
  val trans1 = Utils.buildTask("Transport", List("PR2_ROBOT","GREY_TAPE","TABLE"))
  val trans2 = Utils.buildTask("SearchTransport", List("PR2_ROBOT","GREY_TAPE_1","TABLE")) //_1
  manager ! trans2
//  trans2.chronicle.addConstraint(new MinDelayConstraint(trans2.chronicle.tasks.get(0).end, trans1.chronicle.tasks.get(0).end, 10))
//  manager ! goal2
//  manager ! pickTask
//  manager ! goal
//  manager ! trans1
//  Thread.sleep(10000)
//  manager ! trans2
  //  val options = new PlanningOptions()
  //  options.displaySearch = true

//  val planner = new Planner(pplan, options)
  //  val sol = planner.search(System.currentTimeMillis()+5000)
  //  if(sol == null)
  //    println("Error")
  //  else
  //    println(Printer.actionsInState(sol))
  //  System.exit(0)
}

package fr.laas.fape.acting.simulation

import java.io.File
import java.util.logging.{Level, Logger}

import akka.actor.{ActorSystem, Props}
import fr.laas.fape.acting.{ActivityManager, Utils}
import fr.laas.fape.anml.model.AnmlProblem
import fr.laas.fape.anml.model.concrete.Chronicle
import fr.laas.fape.anml.model.concrete.statements.Persistence
import fr.laas.fape.constraints.stnu.Controllability
import fr.laas.fape.planning.Planning
import fr.laas.fape.planning.core.planning.planner.{Planner, PlanningOptions}
import fr.laas.fape.planning.core.planning.states.modification.ChronicleInsertion
import fr.laas.fape.planning.core.planning.states.{Printer, PartialPlan}
import fr.laas.fape.planning.util.TinyLogger


object Main extends App {
  case class Config(
                     domainFile: File = null,
                     initConfiguration: List[File] = Nil,
                     goals: List[File] = Nil
                   )

  val parser = new scopt.OptionParser[Config]("fape-simu") {
    head("fape-simu", "1.x")

    opt[File]('i', "init").optional()
      .text("File containing the initial knowledge on the environment.")
      .action((x,c) => c.copy(initConfiguration = c.initConfiguration :+ x))

    opt[Seq[File]]('g', "goals").valueName("<goal-file1>,<goal-file2>,...").optional().unbounded()
      .text("One anml file for each goal of the acting system")
      .action((xs,c) => c.copy(goals = c.goals ++ xs))

    arg[File]("<domain-file>").required()
      .text("Domain file.")
      .action( (x, c) => c.copy(domainFile = x))

    help("help").text("Prints this usage text")
  }

  // parser.parse returns Option[C]
  val conf: Config = parser.parse(args, Config()) match {
    case Some(config) => config
    case None => System.exit(1); null // arguments are bad, error message will have been displayed
  }

  Utils.setProblem(conf.domainFile.getAbsolutePath)
  Planning.quiet = false
  Planning.verbose = true
  TinyLogger.logging = true

  Logger.getGlobal.setLevel(Level.OFF)
  Logger.getLogger("ros").setLevel(Level.OFF)
  // Plan.makeDispatchable = true
  // Plan.showChart = true
  // APlanner.logging = false
  // APlanner.debugging = false

  val system = ActorSystem("fape")

  val manager = system.actorOf(Props[ActivityManager], name = "manager")
  val actor = system.actorOf(Props[Dispatcher], name = "dispatcher")

  val goal = Utils.buildTask("GoLook", List("PR2_ROBOT", "TABLE")) //Utils.buildGoal("Table.observed", List("TABLE_0"), "true", 3)
  val goal2 = Utils.buildTask("GoLook", List("PR2_ROBOT", "TABLE_0")) // Utils.buildGoal("Table.observed", List("TABLE"), "true", 3)
  //  manager ! goal
  //  manager ! goal2
  val pickTask = Utils.buildTask("GoPick", List("PR2_ROBOT","GREY_TAPE"))
  manager ! pickTask
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

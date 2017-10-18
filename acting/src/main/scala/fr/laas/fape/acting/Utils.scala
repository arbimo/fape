package fr.laas.fape.acting

import com.sun.org.apache.xpath.internal.operations.VariableSafeAbsRef
import fr.laas.fape.anml.model.AnmlProblem
import fr.laas.fape.anml.model.concrete._
import fr.laas.fape.anml.model.concrete.statements.Persistence
import fr.laas.fape.gui.{ChartWindow, TimedCanvas}
import fr.laas.fape.planning.core.planning.planner.PlanningOptions
import fr.laas.fape.planning.core.planning.search.flaws.finders.NeededObservationsFinder
import fr.laas.fape.planning.core.planning.states.State
import fr.laas.fape.planning.core.planning.states.modification.ChronicleInsertion

import scala.concurrent.ExecutionContext.Implicits.global

object Utils {

  private var problem : AnmlProblem = null

  def options = {
    val opt = new PlanningOptions()
    opt.displaySearch = false
    opt
  }

  lazy val chartWindow = new ChartWindow("Actions")


  def setProblem(file: String): Unit = {
    problem = new AnmlProblem()
    problem.extendWithAnmlFile(file)
  }

  def getProblem = {
    require(problem != null)
    problem
  }

  def buildGoal(svName: String, args: List[String], value: String, deadline: Int = -1) = {
    assert(RefCounter.useGlobalCounter)
    val goal = new Chronicle
    val statement = new Persistence(
      problem.stateVariable(svName, args),
      problem.instance("true"),
      goal,
      RefCounter.getGlobalCounter)
    goal.addStatement(statement)
    if(deadline > -1) {
      goal.addConstraint(new MinDelayConstraint(statement.start, problem.start, -deadline))
    }
    new ChronicleInsertion(goal)
  }

  def buildTask(name: String, args: List[String], deadline: Int = -1) = {
    assert(RefCounter.useGlobalCounter)
    val goal = new Chronicle
    val task = new Task("t-"+name, args.map(problem.instance(_)), None, problem.refCounter)
    goal.addTask(task)
    if(deadline > -1) {
      goal.addConstraint(new MinDelayConstraint(task.end, problem.start, -deadline))
    }
    new ChronicleInsertion(goal)
  }

  def asString(variable: VarRef, plan: State) = {
    plan.domainOf(variable).get(0)
  }

  def Future[T](body: => T) = {
    concurrent.Future {
      try {
        body
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          null
      }
    }
  }
}

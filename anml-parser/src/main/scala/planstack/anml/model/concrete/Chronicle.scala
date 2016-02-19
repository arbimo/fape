package planstack.anml.model.concrete

import java.util

import planstack.FullSTN
import planstack.anml.ANMLException
import planstack.anml.model.abs._
import planstack.anml.model.abs.statements.{AbstractLogStatement, AbstractResourceStatement, AbstractStatement}
import planstack.anml.model.concrete.statements.{LogStatement, ResourceStatement, Statement}
import planstack.anml.model.{AnmlProblem, Context}
import planstack.structures.IList

import scala.collection.JavaConversions._

/** A chronicle decribes modifications to be made to plan.
  *
  * Notable classes implementing it are [[planstack.anml.model.concrete.Action]] and [[planstack.anml.model.AnmlProblem]]
  * Updates to a problem (such as the happening of exogeneous events) are also encoded as StateModifiers
  * in [[planstack.anml.model.AnmlProblem]].
  *
  * Components:
  *  - `vars`: global variables that need to be declared for applying the chronicle.
  *  - `statements`: individual statements depicting a condition of a change on a state variable. Those
  *    come from the effects/preconditions of actions, conditions on decomposition or exogeneous events.
  *  - `actions`: actions to be inserted in the plan. Note that actions are StateModifiers themselves.
  *
  */
trait Chronicle {

  /** A temporal interval in which the chronicle is applied. For instance, if this chronicle refers to
    * an action, the container would refer to the [start, end] interval of this action.
    * ANML temporal annotations such as [start] refer to this temporal interval.
    * Note that time points might appear outside this interval, for instance with the annotations
    * [start-10], [end+10] or [7].
    */
  def container : TemporalInterval

  /** Temporally annotated statements to be inserted in the plan */
  def statements : java.util.List[Statement]

  /** Constraints over constant functions and variables */
  def bindingConstraints : java.util.List[BindingConstraint]

  /** Returns all logical statements */
  def logStatements : java.util.List[LogStatement] = seqAsJavaList(statements.filter(_.isInstanceOf[LogStatement]).map(_.asInstanceOf[LogStatement]))

  /** Returns all logical statements */
  def resourceStatements : java.util.List[ResourceStatement] = seqAsJavaList(statements.filter(_.isInstanceOf[ResourceStatement]).map(_.asInstanceOf[ResourceStatement]))

  /** Actions conditions that must be fulfilled by the plan.
    *
    * An action condition has an action name, a set of parameters and two timepoints.
    * It can be fulfilled/supported by an action with the same whose parameters and
    * time points are equal to those of the action condition.
    */
  def tasks : java.util.List[Task]

  /** (Type, Reference) of global variables to be declared */
  def vars : java.util.List[VarRef]

  var flexibleTimepoints : IList[TPRef] = null
  var anchoredTimepoints : IList[AnchoredTimepoint] = null

  /** All problem instances to be declared
    * Problem instances are typically a global variable with a domain containing only one value (itself).
    */
  def instances : java.util.List[String] = Nil

  protected def temporalConstraints : java.util.List[TemporalConstraint]

  def addAllStatements(absStatements : Seq[AbstractStatement], context:Context, pb:AnmlProblem, refCounter: RefCounter): Unit = {
    for(absStatement <- absStatements) {
      absStatement match {
        case s: AbstractLogStatement =>
          val binded = s.bind(context, pb, this, refCounter)
          statements += binded
          context.addStatement(s.id, binded)

        case s: AbstractResourceStatement =>
          val binded = s.bind(context, pb, this, refCounter)
          statements += binded
          context.addStatement(s.id, binded)

        case s:AbstractTask =>
          val parent = this match {
              case x: Action => Some(x)
              case _ => None
            }
          tasks += Task(pb, s, context, parent, refCounter)

        case _ => throw new ANMLException("unsupported yet:" + absStatement)
      }
    }
  }

  def addAllConstraints(absConstraints : Seq[AbstractConstraint], context:Context, pb:AnmlProblem, refCounter: RefCounter): Unit = {
    for(absConstraint <- absConstraints) {
      absConstraint match {
        case s:AbstractTemporalConstraint =>
          temporalConstraints += s.bind(context, pb, refCounter)

        case s:AbstractBindingConstraint =>
          bindingConstraints += s.bind(context, pb, refCounter)

        case _ => throw new ANMLException("unsupported yet:" + absConstraint)
      }
    }
  }


  def initTemporalObjects() {
    assert(flexibleTimepoints == null)
    assert(anchoredTimepoints == null)
    assert(!this.isInstanceOf[Action], "Error: action should do that themselves")

    val intervals : List[TemporalInterval] = container :: tasks.toList.asInstanceOf[List[TemporalInterval]] ++ statements.toList.asInstanceOf[List[TemporalInterval]]
    val timepoints = (intervals.flatMap(int => List(int.start, int.end)) ++ temporalConstraints.flatMap(tc => List(tc.src, tc.dst))).toSet

    val contingents = temporalConstraints.collect {
      case ParameterizedContingentConstraint(_, ctg, _, _, _, _) => ctg
      case ContingentConstraint(_, ctg, _, _) => ctg
    }

    val stn = new FullSTN[TPRef](timepoints.toSeq)
    val tConstraintsCopy = new util.ArrayList[TemporalConstraint](temporalConstraints)
    this.temporalConstraints.clear()
    tConstraintsCopy.foreach {
      case MinDelayConstraint(from, to, minDelay) => stn.addEdge(to, from, -minDelay)
      case x => this.temporalConstraints.add(x)
    }

    val (flexs, constraints, anchored) = stn.minimalRepresentation(container.start :: container.end :: contingents.toList)

    timepoints.foreach(tp =>
      if(contingents.contains(tp)) tp.setContingent()
      else if(flexs.contains(tp)) tp.setStructural()
      else tp.setVirtual()
    )

    this.flexibleTimepoints = new IList(flexs)
    for(tc <- constraints)
      this.temporalConstraints.add(new MinDelayConstraint(tc.dst, tc.src, -tc.label))

    // no anchored tps
    this.anchoredTimepoints = new IList[AnchoredTimepoint](anchored.map(a => new AnchoredTimepoint(a.timepoint, a.anchor, a.delay)))
  }
}

case class AnchoredTimepoint(timepoint:TPRef, anchor :TPRef, delay :Int)

/**
 * This object sums all time points and constraints in a state modifier.
 * Note that some constraints might apply on time points not defined here
 * (e.g. start of a nested action or global start of the problem).
 *
 * @param timepoints Non virtual time points that should be recorded.
 * @param anchoredTimpepoints Virtual time points, those are defined wrt to another time point. It comes as a tuple
 *                          (virt, anchor, d) where anchor is a non-anchored timepoint d is the distance from virt to anchor
 * @param constraints All constraints between timepoints (excluding constraints involving anchored timepoints)
 */
class TemporalObjects(val timepoints: IList[Pair[TPRef, String]],
                      val anchoredTimpepoints: IList[AnchoredTimepoint],
                      val constraints: IList[MinDelayConstraint])

class BaseChronicle(val container: TemporalInterval) extends Chronicle {

  val statements = new util.LinkedList[Statement]()
  val bindingConstraints = new util.LinkedList[BindingConstraint]()
  val actions = new util.LinkedList[Action]()
  val tasks = new util.LinkedList[Task]()
  val vars = new util.LinkedList[VarRef]()
  override val instances = new util.LinkedList[String]()
  val temporalConstraints = new util.LinkedList[TemporalConstraint]()
}



package planstack.anml.model.abs

import planstack.anml.model._
import planstack.anml.model.abs.time.{IntervalEnd, ContainerEnd, AbsTP}
import planstack.anml.model.concrete._
import planstack.anml.model.concrete.time.TimepointRef
import planstack.anml.parser

abstract class AbstractConstraint {
  def bind(context: Context, pb :AnmlProblem, refCounter: RefCounter) : Constraint
}

abstract class AbstractTemporalConstraint extends AbstractConstraint {

  override final def bind(context: Context, pb :AnmlProblem, refCounter: RefCounter) : TemporalConstraint = this match {
    case AbstractMinDelay(from, to, minDelay) =>
      new MinDelayConstraint(TimepointRef(pb, context, from, refCounter), TimepointRef(pb, context, to, refCounter), minDelay)
    case AbstractParameterizedMinDelay(from, to, minDelay, trans) =>
      new ParameterizedMinDelayConstraint(TimepointRef(pb, context, from, refCounter), TimepointRef(pb, context, to, refCounter), minDelay.bind(context), trans)
    case AbstractParameterizedExactDelay(from, to, delay, trans) =>
      new ParameterizedExactDelayConstraint(TimepointRef(pb, context, from, refCounter), TimepointRef(pb, context, to, refCounter), delay.bind(context), trans)
    case AbstractContingentConstraint(from, to, min, max) =>
      new ContingentConstraint(TimepointRef(pb,context,from, refCounter), TimepointRef(pb,context,to, refCounter), min, max)
  }

  def from : AbsTP
  def to : AbsTP
  def isParameterized : Boolean
}

case class AbstractMinDelay(from:AbsTP, to:AbsTP, minDelay:Integer)
  extends AbstractTemporalConstraint
{
  override def toString = "%s + %s <= %s".format(from, minDelay, to)
  override def isParameterized = false
}

case class AbstractParameterizedMinDelay(from :AbsTP, to :AbsTP, minDelay :AbstractParameterizedStateVariable, trans: (Int => Int))
  extends AbstractTemporalConstraint
{
  override def toString = "%s + %s <= %s".format(from, minDelay, to)
  override def isParameterized = true
}

case class AbstractParameterizedExactDelay(from :AbsTP, to :AbsTP, delay :AbstractParameterizedStateVariable, trans: (Int => Int))
  extends AbstractTemporalConstraint
{
  override def toString = "%s + %s = %s".format(from, delay, to)
  override def isParameterized = true
}

case class AbstractContingentConstraint(from :AbsTP, to :AbsTP, min :Int, max:Int)
  extends AbstractTemporalConstraint
{
  override def toString = s"$from == [$min, $max] ==> $to"
  override def isParameterized = true
}

case class AbstractParameterizedContingentConstraint(from :AbsTP,
                                                     to :AbsTP,
                                                     min :ParameterizedStateVariable,
                                                     max:ParameterizedStateVariable,
                                                     minTrans: (Int => Int),
                                                     maxTrans: (Int => Int))
  extends AbstractTemporalConstraint
{
  override def toString = s"$from == [f1($min), f2($max)] ==> $to"
  override def isParameterized = true
}

object AbstractMaxDelay {
  def apply(from:AbsTP, to:AbsTP, maxDelay:Int) =
    new AbstractMinDelay(to, from, -maxDelay)
}

object AbstractExactDelay {
  def apply(from:AbsTP, to:AbsTP, delay:Int) =
    List(AbstractMinDelay(from, to, delay), AbstractMaxDelay(from,to,delay))
}

object AbstractTemporalConstraint {
  private implicit def atr(tp: planstack.anml.parser.TimepointRef) = AbsTP(tp)

  def apply(parsed: parser.TemporalConstraint) : List[AbstractTemporalConstraint] = parsed match {
    case parser.ReqTemporalConstraint(tp1, "=", tp2, d) =>
      List(AbstractMinDelay(tp2,tp1,d), AbstractMaxDelay(tp2,tp1,d))
    case parser.ReqTemporalConstraint(tp1, "<", tp2, d) =>
      List(AbstractMaxDelay(tp2, tp1, d-1))
    case parser.ContingentConstraint(src,dst,min,max) =>
      List(AbstractContingentConstraint(src,dst,min,max))
  }
}


abstract class AbstractBindingConstraint
  extends AbstractConstraint
{
  override def bind(context: Context, pb: AnmlProblem, refCounter: RefCounter): BindingConstraint = bind(context, pb)

  def bind(context: Context, pb: AnmlProblem) : BindingConstraint
}

class AbstractAssignmentConstraint(val sv : AbstractParameterizedStateVariable, val variable : LVarRef, id:LStatementRef)
  extends AbstractBindingConstraint
{
  require(sv.func.isConstant)

  override def toString = "%s := %s".format(sv, variable)

  override def bind(context: Context, pb: AnmlProblem) =
    new AssignmentConstraint(sv.bind(context), context.getGlobalVar(variable))
}

class AbstractIntAssignmentConstraint(val sv : AbstractParameterizedStateVariable, val value : Int, id:LStatementRef)
  extends AbstractBindingConstraint
{
  require(sv.func.isConstant && sv.func.valueType == "integer")

  override def toString = "%s := %s".format(sv, value)

  override def bind(context: Context, pb: AnmlProblem) =
    new IntegerAssignmentConstraint(sv.bind(context), value)
}

class AbstractEqualityConstraint(val sv : AbstractParameterizedStateVariable, val variable : LVarRef, id:LStatementRef)
  extends AbstractBindingConstraint
{
  require(sv.func.isConstant)

  override def toString = "%s == %s".format(sv, variable)

  override def bind(context: Context, pb: AnmlProblem) =
    new EqualityConstraint(sv.bind(context), context.getGlobalVar(variable))
}

class AbstractVarEqualityConstraint(val leftVar : LVarRef, val rightVar : LVarRef, id:LStatementRef)
  extends AbstractBindingConstraint
{
  override def toString = "%s == %s".format(leftVar, rightVar)

  override def bind(context: Context, pb: AnmlProblem) =
    new VarEqualityConstraint(context.getGlobalVar(leftVar), context.getGlobalVar(rightVar))
}

class AbstractInequalityConstraint(val sv : AbstractParameterizedStateVariable, val variable : LVarRef, id:LStatementRef)
  extends AbstractBindingConstraint
{
  require(sv.func.isConstant)

  override def toString = "%s != %s".format(sv, variable)

  override def bind(context: Context, pb: AnmlProblem) =
    new InequalityConstraint(sv.bind(context), context.getGlobalVar(variable))
}

class AbstractVarInequalityConstraint(val leftVar : LVarRef, val rightVar : LVarRef, id:LStatementRef)
  extends AbstractBindingConstraint
{
  override def toString = "%s != %s".format(leftVar, rightVar)

  override def bind(context: Context, pb: AnmlProblem) =
    new VarInequalityConstraint(context.getGlobalVar(leftVar), context.getGlobalVar(rightVar))
}

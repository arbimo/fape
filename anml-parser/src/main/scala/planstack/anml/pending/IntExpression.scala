package planstack.anml.pending

import planstack.anml.model.concrete.VarRef
import planstack.anml.model._

import scala.collection.mutable

abstract class IntExpression extends Comparable[IntExpression] {
  def trans(transformation: IntExpression => IntExpression) : IntExpression
  def jTrans(t: java.util.function.Function[IntExpression,IntExpression]) : IntExpression = trans((expr:IntExpression) => t.apply(expr))
  def bind(f: LVarRef => VarRef) : IntExpression
  def isParameterized : Boolean
  def lb: Int
  def ub: Int
  def isKnown = lb == ub
  def get = {
    assert(isKnown, "This Value is not known yet.")
    lb
  }

  def asFunction : (VarRef => (Int,Int)) => (Int,Int)
  def allParts : Iterable[IntExpression]
  def allVariables : Set[VarRef] = allParts.collect { case Variable(v, _, _) => v }.toSet

  def plus(o: IntExpression) : IntExpression = IntExpression.sum(this, o)
  def plus(o: Int) : IntExpression = IntExpression.sum(this, IntExpression.lit(o))

  def isInf() = lb >= Int.MaxValue/2-1

  override def compareTo(t: IntExpression): Int =
    if(ub < t.lb)
      ub.compareTo(lb)
    else if(lb > t.ub)
      lb.compareTo(t.ub)
    else
      0
}

object IntExpression {
  val maxes: mutable.Map[(IntExpression,IntExpression),IntExpression] = mutable.Map()
  val minus: mutable.Map[(IntExpression,IntExpression),IntExpression] = mutable.Map()
  val sums: mutable.Map[(IntExpression,IntExpression),IntExpression] = mutable.Map()
  val inversions: mutable.Map[IntExpression,IntExpression] = mutable.Map()
  val locStateVars: mutable.Map[AbstractParameterizedStateVariable, IntExpression] = mutable.Map()
  val literals: mutable.Map[Int, IntExpression] = mutable.Map()
  val stateVariables: mutable.Map[ParameterizedStateVariable, IntExpression] = mutable.Map()
  val variables: mutable.Map[(VarRef,Int,Int), IntExpression] = mutable.Map()

  def safesum(x:Int, y:Int) : Int = {
    val z = x.toLong + y.toLong
    if(z < Int.MinValue)
      Int.MinValue
    else if(z > Int.MaxValue)
      Int.MaxValue
    else z.toInt
  }

  def safeinvert(x: Int) =
    if(x == Int.MaxValue)
      Int.MinValue
    else if(x == Int.MinValue)
      Int.MaxValue
    else
      -x

  def lit(i: Int) = literals.getOrElseUpdate(i, { IntLiteral(i) })
  def locSV(lsv: AbstractParameterizedStateVariable) = locStateVars.getOrElseUpdate(lsv, { LStateVariable(lsv) })
  def stateVariable(sv: ParameterizedStateVariable) = stateVariables.getOrElseUpdate(sv, { StateVariable(sv) })
  def variable(v: VarRef, lb:Int, ub:Int) = variables.getOrElseUpdate((v,lb,ub), { Variable(v,lb,ub) })

  def sum(v1: IntExpression, v2:IntExpression) : IntExpression =
    if(v1.isInf())
      v1
    else if(v2.isInf())
      v2
    else
      (v1, v2) match {
        case (IntLiteral(d1), IntLiteral(d2)) => lit(d1 + d2)
        case (x, y:IntLiteral) => sum(y,x) //always put literals first
        case (IntLiteral(0), x) => x
        case (x:IntLiteral, Max(y,z)) => max(sum(x,y), sum(x,z))
        case (x:IntLiteral, Min(y,z)) => min(sum(x,y), sum(x,z))
        case (IntLiteral(x), Sum(IntLiteral(y),z)) => sum(lit(x+y), z)
        case (x, Invert(y)) if x == y => lit(0)
        case (Invert(y), x) if x == y => lit(0)
        case _ => sums.getOrElseUpdate((v1,v2), {Sum(v1,v2)})
      }

  def max(v1: IntExpression, v2: IntExpression) : IntExpression =
    if(lesserEqual(v1,v2))
      v2
    else if(lesserEqual(v2,v1))
      v1
    else
      (v1,v2) match {
        case (IntLiteral(d1), IntLiteral(d2)) => if(d1 > d2) v1 else v2
        case (x, y:IntLiteral) => max(y, x)
        case (x, Max(y,z)) if (x eq y) || (x eq z) => max(y,z)
        case (x, Min(y,z)) if x eq y => z
        case (x, Min(y,z)) if x eq z => y
        case (Max(y,z), x) if (x eq y) || (x eq z) => max(y,z)
        case (Min(y,z), x) if x eq y => z
        case (Min(y,z), x) if x eq z => y
        case _ if v1 eq v2 => v1
        case _ if lesserEqual(v1,v2) => v2
        case _ => maxes.getOrElseUpdate((v1,v2), {Max(v1,v2)})
      }

  def min(v1: IntExpression, v2: IntExpression) : IntExpression =
    if(v1.isInf())
      v2
    else if(v2.isInf())
      v1
    else
      (v1,v2) match {
        case (IntLiteral(d1), IntLiteral(d2)) => if(d1 < d2) v1 else v2
        case (x, y:IntLiteral) => min(y, x)
        case (x:IntLiteral, Min(y:IntLiteral, z)) => min(min(x,y), z)
        case (Min(x,y), Min(w,z)) if y == z => min(min(x,w), z)
        case _ if v1 eq v2 => v1
        case _ if lesserEqual(v1, v2) => v1
        case _ => minus.getOrElseUpdate((v1,v2), Min(v1,v2))
      }

  def minus(v1: IntExpression) : IntExpression = v1 match {
    case IntLiteral(d) => lit(-d)
    case Invert(v) => v
    case Max(x,y) => min(minus(x), minus(y))
    case Min(x,y) => max(minus(x), minus(y))
    case Sum(x,y) => sum(minus(x), minus(y))
    case _ => inversions.getOrElseUpdate(v1, {Invert(v1)})
  }

  def equals(v1: IntExpression, v2: IntExpression) = (v1,v2) match {
    case (IntLiteral(d1), IntLiteral(d2)) => d1 == d2
    case _ if v1 eq v2 => true
    case _ if lesserEqual(v1,v2) && lesserEqual(v2,v1) => true
    case _ => v1.isKnown && v2.isKnown && v1.get == v2.get
  }
  def lesserThan(v1: IntExpression, v2: IntExpression) = v1.ub < v2.lb
  def lesserEqual(v1: IntExpression, v2: IntExpression) =
    if(v1.isInf())
      false
    else if(v2.isInf())
      false
    else if(v1 eq v2)
      true
    else if(v1.ub <= v2.lb)
      true
    else if(v1.lb > v2.ub)
      false
    else (v1,v2) match {
      case _ => false
    }
}

case class IntLiteral private[pending] (val value: Int) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = this
  override def isParameterized: Boolean = false
  override def lb: Int = value
  override def ub: Int = value

  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) = _ => (value,value)

  override def allParts: Iterable[IntExpression] = List(this)

  override def trans(transformation: (IntExpression) => IntExpression): IntExpression = transformation(this)
  override def toString = value.toString
}

case class LStateVariable private[pending] (val lsv: AbstractParameterizedStateVariable) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = new StateVariable(new ParameterizedStateVariable(lsv.func, lsv.args.map(f).toArray))
  override def isParameterized: Boolean = true

  override val lb: Int = lsv.func.asInstanceOf[IntFunction].minValue
  override val ub: Int = lsv.func.asInstanceOf[IntFunction].maxValue

  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) = throw new RuntimeException("This should have been binded and replaced by a variable first.")
  override def allParts: Iterable[IntExpression] = List(this)

  override def trans(transformation: (IntExpression) => IntExpression): IntExpression = transformation(this)
}

case class StateVariable private[pending] (val sv: ParameterizedStateVariable) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = this
  override def isParameterized: Boolean = true
  override def lb: Int = sv.func.asInstanceOf[IntFunction].minValue
  override def ub: Int = sv.func.asInstanceOf[IntFunction].maxValue
  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) = throw new RuntimeException("This should have been replaced by a variable first.")

  override def allParts: Iterable[IntExpression] = List(this)
  override def trans(transformation: (IntExpression) => IntExpression): IntExpression = transformation(this)
}

case class Variable private[pending] (val variable: VarRef, val lb: Int, val ub: Int) extends IntExpression {
  require(variable.typ == "integer")

  override def bind(f: (LVarRef) => VarRef): IntExpression = this
  override def isParameterized: Boolean = false
  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) = f => f(variable)

  override def allParts: Iterable[IntExpression] = List(this)
  override def trans(transformation: (IntExpression) => IntExpression): IntExpression = transformation(this)
}

case class Max private[pending] (val left: IntExpression, val right: IntExpression) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = IntExpression.max(left.bind(f), right.bind(f))
  override def isParameterized: Boolean = left.isParameterized || right.isParameterized
  override val lb = Math.max(left.lb, right.lb)
  override val ub = Math.max(left.ub, right.ub)

  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) =
    f => {
      val (min1,max1) = left.asFunction.apply(f)
      val (min2,max2) = right.asFunction.apply(f)
      (Math.max(min1,min2), Math.max(max1,max2))
    }

  override def allParts: Iterable[IntExpression] = this :: List(left, right).flatMap(_.allParts)
  override def trans(t: (IntExpression) => IntExpression): IntExpression = IntExpression.max(left.trans(t), right.trans(t))
}

case class Min private[pending] (val left: IntExpression, val right: IntExpression) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = IntExpression.min(left.bind(f), right.bind(f))
  override def isParameterized: Boolean = left.isParameterized || right.isParameterized
  override val lb = Math.min(left.lb, right.lb)
  override val ub = Math.min(left.ub, right.ub)
  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) =
    f => {
      val (min1,max1) = left.asFunction.apply(f)
      val (min2,max2) = right.asFunction.apply(f)
      (Math.min(min1,min2), Math.min(max1,max2))
    }
  override def allParts: Iterable[IntExpression] = this :: List(left, right).flatMap(_.allParts)
  override def trans(t: (IntExpression) => IntExpression): IntExpression = IntExpression.min(left.trans(t), right.trans(t))
}

case class Invert private[pending] (val value: IntExpression) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = IntExpression.minus(value.bind(f))
  override def isParameterized: Boolean = value.isParameterized
  override val lb = IntExpression.safeinvert(value.ub)
  override val ub = IntExpression.safeinvert(value.lb)

  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) =
  f => {
    val (min, max) = value.asFunction.apply(f)
    (-min, -max)
  }
  override def allParts: Iterable[IntExpression] = this :: List(value).flatMap(_.allParts)
  override def trans(t: (IntExpression) => IntExpression): IntExpression = IntExpression.minus(value.trans(t))
}

case class Sum private[pending] (val left: IntExpression, val right: IntExpression) extends IntExpression {
  override def bind(f: (LVarRef) => VarRef): IntExpression = IntExpression.sum(left.bind(f), right.bind(f))
  override def isParameterized: Boolean = left.isParameterized && right.isParameterized
  override val lb = IntExpression.safesum(left.lb, right.lb)
  override val ub = IntExpression.safesum(left.ub, right.ub)
  override def asFunction: ((VarRef) => (Int, Int)) => (Int, Int) =
    f => {
      val (min1,max1) = left.asFunction.apply(f)
      val (min2,max2) = right.asFunction.apply(f)
      (min1 + min2, max1 + max2)
    }
  override def allParts: Iterable[IntExpression] = this :: List(left, right).flatMap(_.allParts)
  override def trans(t: (IntExpression) => IntExpression): IntExpression = IntExpression.sum(left.trans(t), right.trans(t))
}

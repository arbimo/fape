package fr.laas.fape.constraints

import java.util

import fr.laas.fape.anml.model.ParameterizedStateVariable
import fr.laas.fape.anml.model.concrete.{RefCounter, TPRef, VarRef}
import fr.laas.fape.anml.pending.{IntExpression, LStateVariable, StateVariable, Variable}
import fr.laas.fape.constraints.bindings.{BindingConstraintNetwork, IntBindingListener}
import fr.laas.fape.constraints.stnu.structurals.StnWithStructurals
import fr.laas.fape.constraints.stnu.STNU

import scala.collection.JavaConverters._

abstract class PendingConstraint[VarRef, TPRef](val from:TPRef, val to:TPRef)

class PendingContingency[VarRef, TPRef](from:TPRef, to:TPRef,
                                            val min:IntExpression, val max:IntExpression)
  extends PendingConstraint[VarRef, TPRef](from, to)
{
  override def toString = s"$from $to [$min, $max]"
}

class PendingRequirement[VarRef, TPRef](from:TPRef, to:TPRef, val value: IntExpression)
  extends PendingConstraint[VarRef, TPRef](from, to) {
}


/**
 *
 * @param bindings A binding constraints network.
 * @param stn A simple temporal network.
 * @param varsToConstraints Mixed bindings/STN constraints that have not been propagated yet.
 */
class MetaCSP(
                   val bindings: BindingConstraintNetwork,
                   val stn: STNU,
                   protected[constraints] var varsToConstraints: Map[VarRef, List[PendingConstraint[VarRef,TPRef]]]
                 )
  extends IntBindingListener[VarRef]
{

  bindings.setListener(this)

  def this() = this(new BindingConstraintNetwork(), new StnWithStructurals(), Map())

  def this(toCopy : MetaCSP) = this(toCopy.bindings.DeepCopy(), toCopy.stn.deepCopy(), toCopy.varsToConstraints)

  def addRequirement(u :TPRef, v:TPRef, value: IntExpression): Unit = {
    if(value.isKnown) {
      stn.enforceMaxDelay(u, v, value.get)
    } else {
      // declared and bind necessary variables
      assert(!value.isParameterized)
      val pending = new PendingRequirement[VarRef, TPRef](u, v, value)
      for (d <- value.allVariables) {
        varsToConstraints =
          if (varsToConstraints.contains(d))
            varsToConstraints.updated(d, pending :: varsToConstraints(d))
          else
            varsToConstraints.updated(d, List(pending))

        if (bindings.domainOfIntVar(d).size() == 1)
          onBinded(d, bindings.domainOfIntVar(d).get(0))
      }
    }
  }

  def addMinDelay(u: TPRef, v:TPRef, d: IntExpression): Unit = {
    addRequirement(v, u, IntExpression.minus(d))
  }

  def addContingentConstraint(from:TPRef, to:TPRef, min:IntExpression, max:IntExpression): Unit = {
    require(!min.isParameterized, "This IntExpression should have been cleaned of parameterized state variables first.")
    require(!max.isParameterized, "This IntExpression should have been cleaned of parameterized state variables first.")

    if(min.isKnown && max.isKnown) {
      stn.enforceContingent(from, to, min.get, max.get)
    } else {
      val pending = new PendingContingency[VarRef, TPRef](from, to, min, max)
      for (v <- List(min.allVariables, max.allVariables).flatten) {
        varsToConstraints += ((v, pending :: varsToConstraints.getOrElse(v, List())))

        propagateMixedConstraints()

        if (bindings.domainSize(v) == 1)
          onBinded(v, bindings.domainOfIntVar(v).get(0))
      }
    }
  }

  /** Invoked by the binding constraint network when a variable is binded.
    * If this variable is part of a pending constraint, we propagate this one in hte STN.
 *
    * @param variable Integer variable that was binded.
    * @param value Value of the variable.
    */
  override def onBinded(variable: VarRef, value: Int): Unit = {
    val bounds: VarRef => (Int,Int) = v => {
      val dom = bindings.domainOfIntVar(v)
//      assert(dom.asScala.min == dom.get(0))
//      assert(dom.asScala.max == dom.get(dom.size()-1))
      (dom.get(0), dom.get(dom.size()-1))
    }
    if(varsToConstraints contains variable) {
      for (c <- varsToConstraints(variable)) c match {
        case cont:PendingContingency[VarRef,TPRef] => {
          // contingent constraint, add the contingent one if both variable are binded
          val (minLB, minUB) = cont.min.asFunction.apply(bounds)
          val (maxLB, maxUB) = cont.max.asFunction.apply(bounds)
          val bothBinded = minLB == minUB && maxLB == maxUB
          if(bothBinded) {
            // both variables are binded, we add the contingent variable
            stn.enforceContingent(cont.from, cont.to, minLB, maxLB)
          } else {
            // only one variable is binded, simply add a requirement
            stn.enforceMaxDelay(cont.from, cont.to, maxUB)
            stn.enforceMinDelay(cont.from, cont.to, minLB)
          }
        }
        case req:PendingRequirement[VarRef,TPRef] =>
          val (minVal, maxVal) = req.value.asFunction.apply(bounds)
          // requirement (only one variable), propagate it
          stn.enforceMaxDelay(req.from, req.to, maxVal)
      }
      // remove the entries of this variable from the table
      varsToConstraints = varsToConstraints - variable
    }
  }

  def propagateMixedConstraints(): Boolean = {
    try {
      for (pendings <- varsToConstraints.values; pending <- pendings) pending match {
        case req: PendingRequirement[VarRef, TPRef] =>
          assert(req.value.allVariables.size == 1)
          // returns the value to appear on the constraint if the variable was to take this value
          def valueWithBinding(varBinding: Int) =
            req.value.trans({ case Variable(varRef, _, _) => IntExpression.lit(varBinding) }).get

          val minDuration = stn.getMinDelay(req.from, req.to)
          val oldDomain =  bindings.domainOfIntVar(req.value.allVariables.head)

          // only keep values in the domain that will not result in a negative cycle
          val newDom = bindings.domainOfIntVar(req.value.allVariables.head).asScala
            .filter(v => {
              val max = valueWithBinding(v)
              max - minDuration >= 0})
          bindings.restrictIntDomain(req.value.allVariables.head, newDom.asJava)

          // enforce the least constraining value in the domain
          val newMax = newDom.map(x => valueWithBinding(x)).max
          stn.enforceMaxDelay(req.from, req.to, newMax)

        case cont: PendingContingency[VarRef, TPRef] =>
//          val minDuration = stn.getMinDelay(cont.from, cont.to)
//          val maxDuration = stn.getMaxDelay(cont.from, cont.to)
//          bindings.keepValuesBelowOrEqualTo(cont.min, maxDuration)
//          bindings.keepValuesAboveOrEqualTo(cont.max, minDuration)
////          println("mindom: "+bindings.domainOfIntVar(cont.min))
////          println("maxdom: "+bindings.domainOfIntVar(cont.max))
//          val minDelay = bindings.domainOfIntVar(cont.min).asScala.foldLeft(Int.MaxValue)((x, y) => if (x < y) x else y)
//          val maxDelay = bindings.domainOfIntVar(cont.max).asScala.foldLeft(Int.MinValue)((x, y) => if (x > y) x else y)
//          cont.optID match {
//            case Some(id) =>
//              stn.enforceMinDelayWithID(cont.from, cont.to, minDelay, id)
//              stn.enforceMaxDelayWithID(cont.from, cont.to, maxDelay, id)
//            case None =>
//              stn.enforceMinDelay(cont.from, cont.to, minDelay)
//              stn.enforceMaxDelay(cont.from, cont.to, maxDelay)
//          }
      }
      isConsistent
    } catch {
      // TODO: some of the constraint networks do not implement all needed interfaces
      case e:UnsupportedOperationException => true
      case e:NotImplementedError => true
    }
  }

  /** Returns true if both the binding constraint network and the STN are consistent */
  def isConsistent = {
    bindings.isConsistent && stn.isConsistent
  }
}

package planstack.constraints.bindings

import java.util

import planstack.anml.model.Type
import planstack.anml.model.concrete.VarRef
import planstack.constraints.bindings.BindingConstraintNetwork.ExtID

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object BindingConstraintNetwork {
  type DomID = Int
  type ExtID = Int

  var cnt = 0
}

class BindingConstraintNetwork(toCopy: Option[BindingConstraintNetwork]) {

  def this() = this(None)

  import BindingConstraintNetwork.DomID

  private val increment = 10

  var domIds : Array[DomID] = null
  var variables : Array[VarRef] = null
  var domains : Array[Domain] = null

  var vars : ArrayBuffer[ArrayBuffer[VarRef]] = null
  var different : Array[Array[Boolean]] = null
  var values : ArrayBuffer[String] = null
  var valuesIds : Map[String, Int] = null

  var defaultIntDomain : ArrayBuffer[Domain] = null

  /** Extension constraints */
  var extensionConstraints : Map[String, ExtensionConstraint] = null
  var mapping : ArrayBuffer[(mutable.Buffer[DomID], String)] = null


  var extToCheck : mutable.Set[ExtID] = null

  var unusedDomainIds : mutable.Set[DomID] = null

  var hasEmptyDomains = false

  var listener : IntBindingListener[VarRef] = null

  toCopy match {
    case Some(o) =>
      domIds = o.domIds.clone()
      domains = o.domains.clone()
      variables = o.variables.clone()
      vars = o.vars.map(x => x.clone())
      different = o.different.map(x => x.clone())
      values = o.values
      valuesIds = o.valuesIds
      defaultIntDomain = o.defaultIntDomain
      extensionConstraints = o.extensionConstraints
      mapping = o.mapping.clone()
      unusedDomainIds = o.unusedDomainIds.clone()
      extToCheck = o.extToCheck.clone()
    case None =>
      BindingConstraintNetwork.cnt += 1
      domIds = Array.fill(10)(-1) //mutable.Map[VarRef, DomID]()
      variables = Array.fill(10)(null)
      domains = Array.fill(10)(null) //mutable.Map[DomID, ValuesHolder]()
      vars = ArrayBuffer[ArrayBuffer[VarRef]]()
      different = new Array[Array[Boolean]](increment)
      for(i <- different.indices)
        different(i) = Array.fill(increment)(false)

      values = ArrayBuffer[String]()
      valuesIds = Map[String, Int]()
      defaultIntDomain = ArrayBuffer(new Domain(Set()))

      extensionConstraints = Map()
      mapping = ArrayBuffer()

      unusedDomainIds = mutable.Set[DomID]()
      extToCheck = mutable.Set[ExtID]()
  }

  protected[bindings] def allVars = variables

  def domID(v: VarRef) : DomID = domIds(v.id)

  private def allDomIds = vars.indices.filterNot(unusedDomainIds.contains)

  private def newDomID() : DomID = {
    val id =
      if(unusedDomainIds.nonEmpty) {
        val next = unusedDomainIds.head
        unusedDomainIds -= next
        next
      } else {
        val next = vars.size
        next
      }

    if(vars.size > id) vars(id) = ArrayBuffer[VarRef]()
    else vars += ArrayBuffer[VarRef]()

    if(id == different.length) {
      // replace with bigger
      val newConstraints = new Array[Array[Boolean]](different.length *2)
      for(i <- newConstraints.indices)
        newConstraints(i) = Array.fill(different.length *2)(false)

      for(i <- different.indices)
        Array.copy(different(i), 0, newConstraints(i), 0, different(i).length)
      different = newConstraints
    }

    id
  }

  def rawDomain(v: VarRef) : Domain = domains(domID(v))

  def isDiff(v1: VarRef, v2: VarRef) = {
    assert(different(domID(v1))(domID(v2)) == different(domID(v2))(domID(v1)))
    different(domID(v1))(domID(v2))
  }

  private def domainChanged(id: DomID, causedByExtended: Option[ExtID]): Unit = {
    if(domains(id).size() == 0)
      hasEmptyDomains = true

    if(domains(id).size() == 1) {
      // check difference constraints
      val uniqueValue = domains(id).head()
      for (o <- vars.indices
           if !unusedDomainIds.contains(o)
           if different(id)(o)
           if domains(o).contains(uniqueValue))
      {
        domains(o) = domains(o).remove(uniqueValue)

        if (domains(o).isEmpty)
          hasEmptyDomains = true

        domainChanged(o, None)
      }
    }

    // add extended constraints to the queue
    extToCheck ++= (causedByExtended match {
      case Some(extID) => extendedInvolving(id).filter(_ != extID)
      case None => extendedInvolving(id)
    })

    // if it is a integer varaible that got binded, notify the listener if any
    if(listener != null && domains(id).size() == 1 && isIntegerVar(vars(id).head)) {
      val value = domains(id).head()
      for(v <- vars(id)) {
        assert(isIntegerVar(v))
        listener.onBinded(v, value)
      }
    }
  }


  def setListener(listener: IntBindingListener[VarRef]) { this.listener = listener }

  def domainOfIntVar(v: VarRef): util.List[Integer] = {
    assert(isIntegerVar(v))
    rawDomain(v).vals.map(_.asInstanceOf[Integer]).toList.asJava
  }

  def isIntegerVar(v: VarRef): Boolean = v.getType.isNumeric

  def stringValuesAsDomain(stringDomain: util.Collection[String]): Domain =
    new Domain(stringDomain.asScala.map(valuesIds(_)))

  def typeOf(v: VarRef): Type = v.typ

  def unifiable(a: VarRef, b: VarRef): Boolean =
    domID(a) == domID(b) || (!isDiff(a, b) && rawDomain(a).hasOneCommonElement(rawDomain(b)))

  def domainOf(v: VarRef): util.List[String] =
    rawDomain(v).values().asScala.map(values(_)).toList.asJava

  def unified(a: VarRef, b: VarRef): Boolean =
    domID(a) == domID(b) || domainSize(a) == 1 && domainSize(b) == 1 && rawDomain(a).head() == rawDomain(b).head()

  def recordEmptyNAryConstraint(setID: String, isLastValInteger: Boolean, numVariables: Int) = {
    assert(!extensionConstraints.contains(setID))
    extensionConstraints += ((setID, new ExtensionConstraint(isLastValInteger, numVariables)))
  }

  def addAllowedTupleToNAryConstraint(setID: String, values: util.List[String]): Unit = {
    if(!extensionConstraints.contains(setID)) { //TODO: should force usage of record
      extensionConstraints += ((setID, new ExtensionConstraint(false, values.size())))
    }
    val valuesAsIDs = values.asScala.map(valuesIds(_).asInstanceOf[Integer]).toList.asJava
    extensionConstraints(setID).addValues(valuesAsIDs)
  }

  def addAllowedTupleToNAryConstraint(setID: String, values: util.List[String], lastVal: Int): Unit = {
    if(!extensionConstraints.contains(setID)) {
      extensionConstraints += ((setID, new ExtensionConstraint(true, values.size()+1)))
    }
    val valuesAsIDs = values.asScala
      .map(valuesIds(_).asInstanceOf[Integer])

    addPossibleValue(lastVal)
    valuesAsIDs.append(lastVal)

    extensionConstraints(setID).addValues(valuesAsIDs.asJava)
  }


  def separated(a: VarRef, b: VarRef): Boolean =
    isDiff(a, b)

  def separable(a: VarRef, b: VarRef): Boolean =
    !isDiff(a, b) && !unified(a, b)

  def addPossibleValue(value: String) {
    assert(!valuesIds.contains(value))
    valuesIds += ((value, values.size))
    values += value
  }

  def addPossibleValue(value: Int): Unit = {
    if(!defaultIntDomain(0).contains(value)) {
      defaultIntDomain(0) = defaultIntDomain.head.add(value)
    }
  }

  def addNAryConstraint(variables: util.List[VarRef], setID: String): Unit = {
    val asIDs : mutable.Buffer[DomID] = variables.asScala.map(domID)
    assert(asIDs.forall(_ >= 0))
    mapping += ((asIDs, setID))
    extToCheck += mapping.size-1
  }

  def restrictIntDomain(v: VarRef, toValues: util.Collection[Integer]): Unit =
    restrictDomain(v, intValuesAsDomain(toValues))

  def AddSeparationConstraint(a: VarRef, b: VarRef): Unit = {
    if(domID(a) == domID(b))
      hasEmptyDomains = true

    different(domID(a))(domID(b)) = true
    different(domID(b))(domID(a)) = true

    if(domainSize(a) == 1)
      domainChanged(domID(a), None)
    if(domainSize(b) == 1)
      domainChanged(domID(b), None)
  }

  def isConsistent: Boolean = {
    while(extToCheck.nonEmpty && !hasEmptyDomains) {
      val cur = extToCheck.head
      extToCheck -= cur

      checkExtendedConstraint(cur)
    }

    !hasEmptyDomains
  }

  private def checkExtendedConstraint(extID: ExtID): Unit = {
    val (domainsIDs, constraintName) = mapping(extID)

    assert(extensionConstraints.contains(constraintName), "No tuples defined for constant \""+constraintName+
      "\". Have you added any value for it?")

    // process this constraint // TODO check if there is anything new since last time
    val ext = extensionConstraints(constraintName)
    val initialDomains = domainsIDs.map(id => domains(id).values()).toArray
    assert(initialDomains.nonEmpty, "Domain of constraint "+constraintName+" is empty")
    val restrictedDomains = ext.restrictedDomains(initialDomains)

    for(i <- initialDomains.indices) {
      if(initialDomains(i).size() > restrictedDomains(i).size()) {
        domains(domainsIDs(i)) = new Domain(restrictedDomains(i))
        if(domains(domainsIDs(i)).isEmpty)
          hasEmptyDomains = true

        domainChanged(domainsIDs(i), causedByExtended = Some(extID))
      }
    }
  }

  private def extendedInvolving(domID: DomID) : Iterable[ExtID]= {
    for(i <- mapping.indices ; if mapping(i)._1.contains(domID)) yield
      i
  }

  def domainAsString(v: VarRef): String =
    if(isIntegerVar(v))
      "{" + rawDomain(v).vals.mkString(", ") + "}"
    else
      "{"+ rawDomain(v).vals.map(values(_)).mkString(", ") +"}"

  def Report(): String =
    allDomIds.map(id => (id, "["+ vars(id).mkString(", ") +"]", "  "+domainAsString(vars(id).head))).mkString("\n")

  private def merge(id1: DomID, id2: DomID) : Unit = {
    val newDom = domains(id1).intersect(domains(id2))
    val domainUpdated = newDom.size() < domains(id1).size() || newDom.size() < domains(id2).size()

    domains(id1) = newDom
    vars(id1) = vars(id1) ++ vars(id2)
    for(i <- 0 until different(id1).size) {
      different(id1)(i) = different(id1)(i) || different(id2)(i)
      different(i)(id1) = different(i)(id1) || different(i)(id2)
    }
    for(v <- vars(id2))
      domIds(v.id) = id1

    unusedDomainIds += id2
    vars(id2) = new ArrayBuffer[VarRef]()
    for(i <- different.indices) {
      different(i)(id2) = false
      different(id2)(i) = false
    }
    domains(id2) = null // -= id2

    for(i <- mapping.indices) {
      if(mapping(i)._1.contains(id2)) {
        val newDef = mapping(i)._1.map(id => if(id == id2) id1 else id)
        mapping(i) = (newDef, mapping(i)._2)
      }
    }

    // make sure new constraints are propagated
    domainChanged(id1, None)

    assert(allDomIds.forall(id => vars(id).nonEmpty))
  }

  def AddUnificationConstraint(a: VarRef, b: VarRef): Unit = {
    if(domID(a) != domID(b)) {
      merge(domID(a), domID(b))
    }
  }

  def restrictDomain(v: VarRef, domain: Domain): Boolean = {
    val newDom = rawDomain(v).intersect(domain)
    val modified = newDom.size() < rawDomain(v).size()
    if(modified) {
      domains(domID(v)) = newDom
      domainChanged(domID(v), None)
    }
    modified
  }

  def restrictDomain(v: VarRef, toValues: util.Collection[String]): Unit =
    restrictDomain(v, stringValuesAsDomain(toValues))

  def keepValuesAboveOrEqualTo(v: VarRef, min: Int): Unit = {
    assert(isIntegerVar(v))

    val initialDomain = domainOfIntVar(v).asScala
    val newDomain = initialDomain.filter(i => i >= min)
    if(newDomain.size < initialDomain.size) {
      domains(domID(v)) = intValuesAsDomain(newDomain.asJava)
      domainChanged(domID(v), None)
    }
  }
  def keepValuesBelowOrEqualTo(v: VarRef, max: Int): Unit = {
    assert(isIntegerVar(v))

    val initialDomain = domainOfIntVar(v).asScala
    val newDomain = initialDomain.filter(i => i <= max)
    if(newDomain.size < initialDomain.size) {
      domains(domID(v)) = intValuesAsDomain(newDomain.asJava)
      domainChanged(domID(v), None)
    }
  }

  def getUnboundVariables: util.List[VarRef] = {
    val unboundDomains =
      for (domId <- domains.indices ; if domains(domId) != null && domains(domId).size() != 1)
        yield domId
    unboundDomains.map(vars(_).head).filter(!isIntegerVar(_)).toList.asJava
  }

  def addVariable(v: VarRef): Unit = {
    addVariable(v, v.getType.instances.map(i => i.instance).asJava)
  }

  def addVariable(v: VarRef, domain: util.Collection[String]): Unit = {
    assert(!v.typ.isNumeric)
    addVariable(v, stringValuesAsDomain(domain))
  }

  private def ensureSpaceForVar(v:VarRef, futureDomID: Int): Unit = {
    if(v.id >= domIds.length) {
      val tmp = domIds
      domIds = Array.fill(Math.max(domIds.length*2, v.id+1))(-1)
      Array.copy(tmp, 0, domIds, 0, tmp.length)
      variables = util.Arrays.copyOf(variables, Math.max(variables.length*2, v.id+1))
    }
    if(futureDomID >= domains.length) {
      domains = util.Arrays.copyOf(domains, Math.max(domains.length*2, futureDomID+1))
    }

    assert(domIds(v.id) == -1)
    assert(variables(v.id) == null)
    assert(domains(futureDomID) == null)
  }

  private def addVariable(v:VarRef, dom: Domain) {
    assert(!contains(v))
    val domID = newDomID()
    ensureSpaceForVar(v, domID)

    domIds(v.id) = domID
    variables(v.id) = v
    vars(domID) += v
    domains(domID) = dom
    domainChanged(domID, None)

    assert(vars(domID).size == 1)
  }

  def isRecorded(v: VarRef) : Boolean = domIds.length > v.id && domIds(v.id) != -1

  def contains(v: VarRef): Boolean = isRecorded(v)

  def DeepCopy(): BindingConstraintNetwork =
    new BindingConstraintNetwork(Some(this))

  def domainSize(v: VarRef): Integer = rawDomain(v).size()

  def addIntVariable(v: VarRef): Unit = {
    assert(v.typ.isNumeric)
    addVariable(v, defaultIntDomain.head)
  }

  def addIntVariable(v: VarRef, dom: Domain) : Unit = {
    assert(v.typ.isNumeric)
    addVariable(v, dom)
  }

  def addIntVariable(v: VarRef, domain: util.Collection[Integer]): Unit = {
    assert(v.typ.isNumeric)
    addVariable(v, intValuesAsDomain(domain))
  }

  @Deprecated
  def intValuesAsDomain(intDomain: util.Collection[Integer]): Domain = {
    val valuesIds =
      for(value <- intDomain.asScala) yield {
        if(!defaultIntDomain.head.contains(value))
          addPossibleValue(value)
        value.toInt
      }
    new Domain(valuesIds)
  }

  @Deprecated
  def intValueOfRawID(valueID: Integer): Integer = valueID
}

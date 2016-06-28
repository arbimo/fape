package planstack.anml.model

import planstack.anml.model.concrete.{InstanceRef, EmptyVarRef, RefCounter, VarRef}


class PartialContext(pb: AnmlProblem, val parentContext:Option[AbstractContext]) extends AbstractContext(pb) {

  def addUndefinedVar(v:LVarRef, refCounter: RefCounter = null) {
    assert(!variables.contains(v), "Local variable already defined: "+v)
    nameToLocalVar.put(v.id, v)
    variables.put(v, new EmptyVarRef(v.typ))
  }

  def bindVarToConstant(name:LVarRef, const:InstanceRef): Unit = {
    assert(variables.contains(name))
    assert(variables(name).isInstanceOf[EmptyVarRef])
    variables.put(name, const)
  }

  def addUndefinedAction(localID:LActRef) {
    assert(!actions.contains(localID))
    actions.put(localID, null)
  }

  /**
   * Creates a new local var with type tipe. Returns the name of the created variable.
 *
   * @param typ Type of the variable to create
   * @return Name of the new local variable
   */
  def getNewLocalVar(typ:Type) : LVarRef = {
    var i = 0
    var lVarRef = new LVarRef("locVar_"+i, typ)
    while(contains(lVarRef)) {
      i += 1
      lVarRef = new LVarRef("locVar_"+i, typ)
    }
    addUndefinedVar(lVarRef)
    lVarRef
  }

  /**
   * Builds a new concrete context (i.e. all local vars map to a global var) by
   *  - adding all (local, global) variable pairs to the new context.
   *  - creating the missing global variables using `factory`
 *
   * @param parent Concrete context to be added as the parent of the built context
   * @param newVars map of (localVar -> globalVar) to be added to the context)
   * @return
   */
  def buildContext(pb:AnmlProblem, parent:Option[Context], refCounter: RefCounter, newVars:Map[LVarRef, VarRef] = Map()) = {
    val context = new Context(pb, parent)

    for((local, global) <- variables) {
      if(global.isEmpty && newVars.contains(local)) {
        context.addVar(local, newVars(local))
      } else if(global.isEmpty) {
        val globalVar = new VarRef(global.typ, refCounter)
        context.addVar(local, globalVar)
        context.addVarToCreate(globalVar)
      } else {
        context.addVar(local, global)
      }
    }

    for((localActionID, action) <- actions) {
      context.addAction(localActionID, action)
    }
    context
  }

}

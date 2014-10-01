package planstack.constraints.stnu

class STNULabel(val value : Int) {

  def req = this.isInstanceOf[Requirement]
  def posReq = req && positive
  def negReq = req && negative
  def cont = this.isInstanceOf[Contingent]
  def cond = this.isInstanceOf[Conditional]
  def negative = value < 0
  def positive = value >= 0
  def node : Int = throw new RuntimeException("This label has no conditional node: "+this)
}

class Requirement(value : Int) extends STNULabel(value) {
  override def toString = "req: "+value
}

class Contingent(value : Int) extends STNULabel(value) {
  override def toString = "cont: "+value
}

class Conditional(override val node : Int, value : Int) extends  STNULabel(value) {
  override def toString = "<%s, %d>".format(node, value)
}


/*
class EIDC extends ISTNU with EDG {

  val ddg = new DirectedMultiLabeledIIAdjList[Int]()

  def consistent = throw new RuntimeException("ksdmklfqdsfsd qfsdfkqsdfk")



  def checkCycleOfNegative() : Boolean = false

  def edgeAdded(e : E) {}

  /*
  def efficientIDC(e : LabeledEdge[Int, STNULabel]) : Boolean = {
    val todo = ListBuffer[Int]()

    todo += e.v

    e.l match  {
      case l:Contingent if l.value <0 && ccgraph.edge(e.u, e.v).isEmpty => {
        ccgraph.addEdge(e.u, e.v)
        if(!checkCycleOfNegative) return false
        if(!todo.contains(e.u)) todo += e.u
      }
      case _ => // nothing to do
    }

    while(todo.nonEmpty) {
      val current = todo.filter(n => {
        ccgraph.inEdges(n).forall(e => !todo.contains(e.u))
      }).headOption match {
        case Some(node) => node
        case None => throw new RuntimeException("Error: unable to select a current node")
      }

      processCond(current)
      processNegReq(current)
      processPosReq(current)
    }

    true
  }
  */

  def processCond(n : Int) {

  }

  def processNegReq(n : Int) {

  }

  def processPosReq(n : Int) {

  }





  // Members declared in planstack.constraints.stn.ISTN
  def checkConsistency(): Boolean = ???
  def checkConsistencyFromScratch(): Boolean = ???
  def earliestStart(u: Int): Int = ???
  def latestStart(u: Int): Int = ???
  def writeToDotFile(file: String): Unit = ???

  // Members declared in planstack.constraints.stnu.ISTNU
  def cc(): planstack.constraints.stnu.ISTNU = ???
  def size: Int = ???
}
*/
package planstack.graph

import org.scalatest.Suite
import planstack.graph.core.{LabeledEdge, LabeledGraph}


trait LabeledGraphSuite[V,EdgeLabel] extends BaseGraphSuite[V, EdgeLabel, LabeledEdge[V,EdgeLabel]] {

  private def g = graph.asInstanceOf[LabeledGraph[V,EdgeLabel]]

  def testLabeledType { assert(graph.isInstanceOf[LabeledGraph[V,EdgeLabel]])}

}

package planstack.anml.model.abs.time

import planstack.anml.parser







case class AbstractTemporalAnnotation(start:AbstractRelativeTimePoint, end:AbstractRelativeTimePoint, flag:String) {
  require(flag =="is" || flag == "contains")

  override def toString =
    if(flag == "is") "[%s, %s]".format(start, end)
    else "[%s, %s] contains".format(start, end)
}

object AbstractTemporalAnnotation {

  def apply(annot:parser.TemporalAnnotation) : AbstractTemporalAnnotation = {
    new AbstractTemporalAnnotation(AbstractRelativeTimePoint(annot.start), AbstractRelativeTimePoint(annot.end), annot.flag)
  }
}

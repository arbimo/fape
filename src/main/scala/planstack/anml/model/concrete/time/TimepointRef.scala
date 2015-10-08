package planstack.anml.model.concrete.time

import planstack.anml.ANMLException
import planstack.anml.model._
import planstack.anml.model.abs.time._
import planstack.anml.model.concrete.{GlobalRef, TPRef, TemporalInterval}

/** Reference to a time point, it is used to represent timepoints such as the beginning of an action
  * which would be written `start(a2)` in ANML.
  *
  * @param extractor A String representing which time-point to extract. Possible values are: `"GStart"` (global start
  *                  of the anml problem), `"GEnd"` (global end of the ANML problem), `"start"` (start of the
  *                  [[planstack.anml.model.concrete.TemporalInterval]] refered to by `id`) and `"end"` (end of the
  *                  [[planstack.anml.model.concrete.TemporalInterval]] refered to by `id`).
  * @param id Global reference to an interval from which to extract the time point. If the reference is empty,
  *           It refers to the containing [[planstack.anml.model.concrete.TemporalInterval]]
  */
class TimepointRef(val extractor:String, val id:GlobalRef) {
  require(Set("GStart","GEnd","start","end").contains(extractor))

  override def toString = (extractor, id) match {
  case ("GStart", _) => "GStart"
  case ("GEnd", _) => "GEnd"
  case (ext, ident) =>
  if(ident.isEmpty) ext
  else "%s(%s)".format(ext, ident)
  }
}

object TimepointRef {

  def apply(pb:AnmlProblem, context:Context, abs:AbsTP) : TPRef = {
    abs match {
      case TimeOrigin => pb.start
      case ContainerStart => context.interval.start
      case ContainerEnd => context.interval.end
      case IntervalStart(id) => context.getIntervalWithID(id).start
      case IntervalEnd(id) => context.getIntervalWithID(id).end
    }
  }
}

package fr.laas.fape.constraints.meta.stn.events

import fr.laas.fape.constraints.meta.CSP
import fr.laas.fape.constraints.meta.events._
import fr.laas.fape.constraints.meta.stn.constraint.TemporalConstraint
import fr.laas.fape.constraints.meta.stn.core.{IDistanceChangeListener, StnWithStructurals}
import fr.laas.fape.constraints.meta.stn.variables.{TemporalDelay, Timepoint}

class STNEventHandler(val stn: StnWithStructurals, implicit val csp: CSP)
  extends IEventHandler with IDistanceChangeListener {
  require(csp.stn == stn)

  override def handleEvent(event: Event): Unit = event match {
    case NewVariableEvent(tp: Timepoint) =>
      if(tp == csp.temporalOrigin) {
        stn.recordTimePointAsStart(tp)
      } else if(tp == csp.temporalHorizon) {
        stn.recordTimePointAsEnd(tp)
      } else {
        stn.recordTimePoint(tp)
        stn.enforceBefore(csp.temporalOrigin, tp)
        stn.enforceBefore(tp, csp.temporalHorizon)
      }
    case NewConstraintEvent(c: TemporalConstraint) =>
      // nothing to do, handled in propagation directly
    case NewConstraintEvent(c) =>
      // not a pure temporal constraint, check if there is any temporal variables to watch in it
      for(v <- c.variables) {
        v match {
          case tp: Timepoint =>
            stn.addWatchedDistance(csp.temporalOrigin, tp, this)
          case d: TemporalDelay =>
            stn.addWatchedDistance(d.from, d.to, this)
          case _ => // ignore constraint
        }
      }

    case _ =>
  }

  override def distanceUpdated(tp1: Timepoint, tp2: Timepoint) {
    csp.addEvent(DomainReduced(csp.varStore.getDelayVariable(tp1, tp2)))
    if(tp1 == csp.temporalOrigin)
      csp.addEvent(DomainReduced(tp2))
  }
}
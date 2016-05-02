package fape.core.planning.search.flaws.resolvers;

import fape.core.planning.planner.Planner;
import fape.core.planning.states.State;
import fape.core.planning.timelines.Timeline;
import fape.util.TinyLogger;
import planstack.anml.model.LStatementRef;
import planstack.anml.model.abs.AbstractAction;
import planstack.anml.model.concrete.Action;
import planstack.anml.model.concrete.Factory;
import planstack.anml.model.concrete.statements.LogStatement;
import planstack.anml.model.concrete.statements.Statement;

/**
 * A resolver for an open goal. The supporting statement is brought by a new action to be inserted.
 * Optionally, a decomposition ID might be provided (decID != -1). If this is the case,
 * the supporting statement will be taken from the statements of the decomposition.
 */
public class SupportingAction extends Resolver {

    public final AbstractAction act;
    public final int consumerID;
    /** id of the statement used for support */
    public final LStatementRef statementRef;

    public SupportingAction(AbstractAction act, LStatementRef statementRef, Timeline consumer) {
        this.act = act;
        this.consumerID = consumer.mID;
        this.statementRef = statementRef;
    }

    @Override
    public String toString() {
        return "Supporting action: "+act;
    }

    @Override
    public boolean apply(State st, Planner planner, boolean isFastForwarding) {
        final Timeline consumer = st.getTimeline(consumerID);

        assert consumer != null : "Consumer was not found.";

        Action action = Factory.getStandaloneAction(st.pb, act, st.refCounter);
        st.insert(action);

        // statement that should be supporting our consumer
        Statement supporter = action.context().getStatement(statementRef);
        assert supporter != null && supporter instanceof LogStatement;

        if(st.canBeEnabler((LogStatement) supporter, consumer)) {
            final Timeline supportingDatabase = st.getDBContaining((LogStatement) supporter);
            // add the causal link
            Resolver opt = new SupportingTimeline(supportingDatabase.mID, supportingDatabase.numChanges()-1, consumer);
            TinyLogger.LogInfo(st, "     [%s] Adding %s", st.mID, opt);
            return opt.apply(st, planner, isFastForwarding);
        } else {
            // turns out this statement cannot support our database.
            return false;
        }

    }

    @Override
    public int compareWithSameClass(Resolver e) {
        assert e instanceof SupportingAction;
        SupportingAction o = (SupportingAction) e;
        if(act != o.act)
            return act.name().compareTo(o.act.name());

        if(consumerID != o.consumerID)
            return consumerID - o.consumerID;

        assert !statementRef.equals(o.statementRef) : "Error: trying to compare to identical resolvers";
        return statementRef.id().compareTo(o.statementRef.id());
    }
}

package fape.core.planning.search.flaws.resolvers;

import fape.core.planning.planner.Planner;
import fape.core.planning.states.State;
import fape.core.planning.timelines.Timeline;
import planstack.anml.model.abs.AbstractAction;
import planstack.anml.model.concrete.Action;
import planstack.anml.model.concrete.Factory;
import planstack.anml.model.concrete.Task;

public class SupportingTaskDecomposition implements Resolver {

    public final Task task;
    public final AbstractAction abs;
    public final Timeline tl;

    public SupportingTaskDecomposition(Task task, AbstractAction method, Timeline consumer) {
        this.task = task;
        this.abs = method;
        this.tl = consumer;
    }

    @Override
    public boolean apply(State st, Planner planner, boolean isFastForwarding) {
        // create a new action with the same args as the condition
        Action act = Factory.getInstantiatedAction(st.pb, abs, task.args(), st.refCounter);
        st.insert(act);

        // enforce equality of time points and add support to task network
        st.addSupport(task, act);

        // make sure this open goal can only be solved by a statement from this action or one of its child
        st.addSupportConstraint(tl.getFirst(), act);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int compareWithSameClass(Resolver e) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

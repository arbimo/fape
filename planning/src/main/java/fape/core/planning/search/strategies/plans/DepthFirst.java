package fape.core.planning.search.strategies.plans;

import fape.core.planning.states.State;

/**
 * Depth first search strategy: the deepest plan is always selected first.
 */
public class DepthFirst extends PartialPlanComparator {
    @Override
    public String shortName() {
        return "dfs";
    }

    @Override
    public String reportOnState(State st) {
        return String.format("DFS:\t depth: %s", st.getDepth());
    }

    @Override
    public float g(State st) {
        return 10000f - st.getDepth();
    }

    @Override
    public float h(State st) {
        return 0f;
    }

    @Override
    public float hc(State st) {
        return 0;
    }
}

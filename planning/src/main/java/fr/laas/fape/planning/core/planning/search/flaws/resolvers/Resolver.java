package fr.laas.fape.planning.core.planning.search.flaws.resolvers;

import fr.laas.fape.planning.core.planning.states.PartialPlan;
import fr.laas.fape.planning.core.planning.states.modification.PartialPlanModification;


/**
 * A resolver is recipe to fix a Flaw.
 * It provides an apply method that modifies a state so that the flaw is fixed.
 */
public interface Resolver extends Comparable<Resolver> {

    default PartialPlanModification asStateModification(PartialPlan partialPlan) { throw new UnsupportedOperationException(); }

    /**
     * Should provide a comparison with another resolver of the same class.
     * This is used to sort resolvers for reproducibility.
     */
    int compareWithSameClass(Resolver e);

    /**
     * Provides a way to sort resolvers for reproducibility. This is not intended to give information
     * on how much interesting a resolver is but just to make sure that resolvers are always in the same
     * order between two runs.
     */
    @Override
    default int compareTo(Resolver o) {
        String n1 = this.getClass().getCanonicalName();
        String n2 = o.getClass().getCanonicalName();
        int cmp = n1.compareTo(n2);
        if(cmp != 0) {
            assert this.getClass() != o.getClass();
            return -cmp;
        } else {
            assert this.getClass() == o.getClass();
            int result = this.compareWithSameClass(o);
            assert result != 0 : "There must be a total and deterministic order between the resolvers.";
            return -result;
        }
    }
}

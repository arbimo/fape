package fr.laas.fape.planning.core.planning.search.flaws.resolvers;

import fr.laas.fape.planning.core.planning.states.PartialPlan;
import fr.laas.fape.planning.core.planning.states.modification.PartialPlanModification;
import fr.laas.fape.planning.core.planning.states.modification.SupportRestriction;
import fr.laas.fape.planning.core.planning.timelines.Timeline;
import lombok.Value;
import fr.laas.fape.anml.model.concrete.Task;

@Value
public class FutureTaskSupport implements Resolver {

    private final Timeline consumer;
    private final Task task;

    @Override
    public PartialPlanModification asStateModification(PartialPlan partialPlan) {
        return new SupportRestriction(consumer.getFirst().getFirst(), task);
    }

    @Override
    public int compareWithSameClass(Resolver e) {
        assert e instanceof FutureTaskSupport;
        FutureTaskSupport fts = (FutureTaskSupport) e;
        assert fts.consumer == consumer;

        return Integer.compare(task.start().id(), fts.task.start().id());
    }
}

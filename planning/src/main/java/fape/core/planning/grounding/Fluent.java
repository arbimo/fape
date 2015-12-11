package fape.core.planning.grounding;

import fape.core.inference.Term;
import fr.laas.fape.structures.AbsIdentifiable;
import fr.laas.fape.structures.Ident;
import fr.laas.fape.structures.ValueConstructor;
import planstack.anml.model.concrete.InstanceRef;


@Ident(Fluent.class)
public final class Fluent extends AbsIdentifiable implements Term {
    final public GStateVariable sv;
    final public InstanceRef value;

    /** Creates a new Fluent representing the state variable sv with value 'value".
     *
     *  This constructor should only be invoked by an IRStorage (e.g. store in preprocessor).
     *  If you need a new FLuent, you should invoke Preprocessor.getFluent(...)
     */
    @ValueConstructor @Deprecated
    public Fluent(final GStateVariable sv, final InstanceRef value) {
        this.sv = sv;
        this.value = value;
    }

    @Override
    public String toString() {
        return sv +"=" + value;
    }
}

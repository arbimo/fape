package fape.core.planning.preprocessing;


import java.util.List;

/**
* Encodes the type of "fluent".
*  - name of the predicate (part of the state variable)
*  - type of the argument of the predicate/state variable
*  - type of the value
*/
class FluentType {
    public final String predicateName;
    public final List<String> argTypes;
    public final String valueType;

    /*
    public FluentType(ParameterizedStateVariable sv, VariableRef value) {
        this.predicateName = sv.predicateName;
        this.argType = sv.variable.type;
        this.valueType = value.type;
    }
*/
    public FluentType(String predicate, List<String> argTypes, String valueType) {
        this.predicateName = predicate;
        this.argTypes = argTypes;
        this.valueType = valueType;
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof FluentType)) {
            return false;
        } else if(!predicateName.equals(((FluentType) o).predicateName)) {
            return false;
        } else if(!valueType.equals(((FluentType) o).valueType)){
            return false;
        } else {
            FluentType ft = (FluentType) o;
            for(int i=0 ; i<argTypes.size() ; i++) {
                if(!argTypes.get(i).equals(ft.argTypes.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int hashCode() {
        int i=0;
        int hash = predicateName.hashCode() *42 * i++;
        hash += valueType.hashCode() * 42 * i++;
        for(String argType : argTypes) {
            hash += argType.hashCode() * 42 * i++;
        }
        return hash;
    }

    @Override
    public String toString() {
        return predicateName+"("+argTypes+"):"+valueType;
    }
}
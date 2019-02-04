package fr.laas.fape.structures;


import java.lang.reflect.Constructor;
import java.util.function.Function;

public class Desc<T> {
    final Class<T> described;
    final Class ident;
    final Function<Object[], Identifiable> ctor;
    public Desc(Class described, Class ident, Function<Object[], Identifiable> ctor) {
        this.described = described;
        this.ident = ident;
        this.ctor = ctor;
    }

    public static <T> Desc<T> getAbstract(Class<T> clazz) {
        try {
            final Class identClazz = getIdentClass(clazz);
            assert identClazz != null;
            return new Desc(clazz, identClazz, null);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static <T> Desc<T> get(Class<T> clazz) {
        try {
            final Class identClazz = getIdentClass(clazz);
            assert identClazz != null;
            Constructor c = null;
            for(Constructor candidate : clazz.getDeclaredConstructors()) {
                if(candidate.getAnnotationsByType(ValueConstructor.class).length > 0) {
                    if(c != null)
                        throw new RuntimeException("Two annotated constructors on class: "+clazz.getName());
                    c = candidate;
                }
            }
            if(c == null)
                throw new RuntimeException("No constructor annotated with @ValueConstructor in class: "+clazz.getName());
            else {
                Constructor ctor = c;
                Function<Object[], Identifiable> ctorFun = params -> {
                    try {
                        return (Identifiable) ctor.newInstance(params);
                    } catch(Exception e) {
                        throw new RuntimeException(e);
                    }
                };
                return new Desc(clazz, identClazz, ctorFun);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    private static Class getIdentClass(Class clazz) {
        assert clazz.getAnnotation(Ident.class) != null : clazz.toString()+" has no Ident annotation.";
        return ((Ident) clazz.getAnnotation(Ident.class)).value();
    }
}
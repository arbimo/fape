package fr.laas.fape.structures;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"rawtypes", "unchecked"})
public class IRStorage {


    Map<Class, Map<List<Object>, Identifiable>> instancesByParams = new HashMap<>();
    Map<Class, ArrayList<Identifiable>> instances = new HashMap<>();



    public Object get(Desc desc, List<Object> params) {
        try {
            final Class identClazz = desc.ident;

            List<Object> paramsAndClass = new ImmutableList<>(params, desc.described);

            instancesByParams.computeIfAbsent(identClazz, x -> new HashMap<>());
            instances.computeIfAbsent(identClazz, x -> new ArrayList<>());

            if (instancesByParams.get(identClazz).containsKey(paramsAndClass)) {
                return instancesByParams.get(identClazz).get(paramsAndClass);
            }

            else {
                Function<Object[], Identifiable> ctor = desc.ctor;
                if(ctor == null) {
                    throw new RuntimeException("Class "+desc.described+ " is not recorded statically");
                }
                Identifiable n = ctor.apply(params.toArray());
                n.setID(instances.get(identClazz).size());
                instances.get(identClazz).add(n);
                instancesByParams.get(identClazz).put(paramsAndClass, n);
                return n;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Identifiable get(Desc desc, int id) {
        try {
            return instances.get(desc.ident).get(id);
        } catch (IndexOutOfBoundsException e) {
            throw new NoSuchElementException("No instance of class "+desc.described.getName()+" with ID: "+id);
        }
    }

    public int getHigherID(Desc desc) {
        return instances.get(desc.ident).size();
    }

    public void record(Identifiable o) {
        assert o.getID() >= 0;
        Class identClazz = o.descriptor().ident;
        instances.putIfAbsent(identClazz, new ArrayList<>(50));
        ArrayList<Identifiable> allVals = instances.get(identClazz);
        while(allVals.size() <= 1+o.getID())
            allVals.add(null);
        assert allVals.get(o.getID()) == null || allVals.get(o.getID()) == o;
        allVals.set(o.getID(), o);
    }

    public <T extends Identifiable> IntRep<T> getIntRep(Desc<T> desc) {
        final Class identClazz = desc.ident;
        instancesByParams.putIfAbsent(identClazz, new HashMap<>());
        instances.putIfAbsent(identClazz, new ArrayList<>());

        return new IntRep<T>() {
            final ArrayList<Identifiable> values = instances.get(identClazz);
            public int asInt(T t) { return t.getID(); }

            @Override @SuppressWarnings("unchecked")
            public T fromInt(int id) { return (T) values.get(id); }

            @Override
            public boolean hasRepresentation(T t) { return true; }
        };
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getInstances(Desc<T> desc) {
        final Class identClazz = desc.ident;
        instancesByParams.putIfAbsent(identClazz, new HashMap<>());
        instances.putIfAbsent(identClazz, new ArrayList<>());

        return instances.get(identClazz).stream()
                .filter(o -> desc.described.isInstance(o))
                .map(o -> (T) o)
                .collect(Collectors.toList());
    }
}

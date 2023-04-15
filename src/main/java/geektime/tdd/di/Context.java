package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

public class Context {
    private final Map<Class<?>, Provider<?>> providers = new HashMap<>();

    public <Type> void bind(Class<Type> type, Type instance) {
        providers.put(type, (Provider<Type>) () -> instance);
    }

    public <Type, Implementation extends Type>
    void bind(Class<Type> type, Class<Implementation> implementation) {
        long injectConstructorsCount = stream(implementation.getConstructors()).filter(c -> c.isAnnotationPresent(Inject.class)).count();
        if (injectConstructorsCount > 1) {
            throw new IllegalComponentException();
        }
        if (injectConstructorsCount == 0 &&
                stream(implementation.getConstructors()).noneMatch(c -> c.getParameters().length == 0)
        ) throw new IllegalComponentException();
        providers.put(type, (Provider<Type>) () -> {
            try {
                Constructor<Implementation> constructor = getConstructor(implementation);
                Object[] dependencies = stream(constructor.getParameters()).map(p -> get(p.getType())).toArray();
                return (Type) constructor.newInstance(dependencies);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static <Type> Constructor<Type> getConstructor(Class<Type> implementation) {
        Stream<Constructor<?>> injectConstructors = stream(implementation.getConstructors()).filter(c -> c.isAnnotationPresent(Inject.class));
        return (Constructor<Type>) injectConstructors.findFirst().orElseGet(() -> {
            try {
                return implementation.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public <Type> Type get(Class<Type> type) {
        return (Type) providers.get(type).get();
    }


}

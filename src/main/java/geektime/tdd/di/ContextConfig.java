package geektime.tdd.di;

import jakarta.inject.Provider;

import java.util.*;

import static java.util.List.of;

public class ContextConfig {
    private final Map<Class<?>, ComponentProvider<?>> providers = new HashMap<>();

    public <Type> void bind(Class<Type> type, Type instance) {
        providers.put(type, (ComponentProvider<Type>) context -> instance);
    }

    public <Type, Implementation extends Type> void bind(Class<Type> type, Class<Implementation> implementation) {
        providers.put(type, new InjectionProvider<>(implementation));
    }

    public Context getContext() {
        providers.keySet().forEach(component -> checkDependencies(component, new Stack<>()));
        return new Context() {

            @Override
            public Optional<?> get(Ref ref) {
                if (ref.isContainerType()) {
                    if (ref.getContainer() != Provider.class) return Optional.empty();
                    return Optional.ofNullable(providers.get(ref.getComponent()))
                            .map(provider -> (Provider<Object>) () -> provider.get(this));
                }
                return Optional.ofNullable(providers.get((ref.getComponent()))).map(provider -> (Object) provider.get(this));
            }

        };
    }

    private void checkDependencies(Class<?> component, Stack<Class<?>> visiting) {
        for (Context.Ref ref : providers.get(component).getDependencies()) {
            Class<?> componentType = ref.getComponent();
            if (!providers.containsKey(componentType)) throw new DependencyNotFoundException(component, componentType);
            if (!ref.isContainerType()) {
                if (visiting.contains(componentType)) throw new CyclicDependenciesFoundException(visiting);
                visiting.push(componentType);
                checkDependencies(componentType, visiting);
                visiting.pop();
            }
        }
    }

    interface ComponentProvider<T> {
        T get(Context context);

        default List<Context.Ref> getDependencies() {
            return of();
        }

    }

}

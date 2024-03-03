package geektime.tdd.di;

import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;
import java.util.function.Function;

import static java.util.List.of;

public class ContextConfig {

    private final Map<Component, ComponentProvider<?>> components = new HashMap<>();
    private final Map<Class<?>, Function<ComponentProvider<?>,ComponentProvider<?>>> scopes = new HashMap<>();

    public ContextConfig() {
        scopes.put(Singleton.class, SingletonProvider::new);
    }

    public <Type> void bind(Class<Type> type, Type instance) {
        components.put(new Component(type, null), (ComponentProvider<Type>) context -> instance);
    }

    public <Type> void bind(Class<Type> type, Type instance, Annotation... qualifiers) {
        if (Arrays.stream(qualifiers).anyMatch(q -> !q.annotationType().isAnnotationPresent(Qualifier.class))) {
            throw new IllegalComponentException();
        }
        for (Annotation qualifier : qualifiers) {
            components.put(new Component(type, qualifier), (ComponentProvider<Type>) context -> instance);
        }
    }

    public <Type, Implementation extends Type> void bind(Class<Type> type, Class<Implementation> implementation) {
        bind(type,implementation,implementation.getAnnotations());
    }

    public <Type, Implementation extends Type> void bind(Class<Type> type, Class<Implementation> implementation, Annotation... annotations) {
        if (Arrays.stream(annotations).map(Annotation::annotationType)
                .anyMatch(t -> !t.isAnnotationPresent(Qualifier.class)&&!t.isAnnotationPresent(Scope.class))) {
            throw new IllegalComponentException();
        }

        Optional<Annotation> scopeForType = Arrays.stream(implementation.getAnnotations()).filter(t -> t.annotationType().isAnnotationPresent(Scope.class)).findFirst();

        List<Annotation> qualifiers = Arrays.stream(annotations).filter(t -> t.annotationType().isAnnotationPresent(Qualifier.class)).toList();
        Optional<Annotation> scope = Arrays.stream(annotations).filter(t -> t.annotationType().isAnnotationPresent(Scope.class)).findFirst()
                .or(()->scopeForType);

        ComponentProvider<?> injectionProvider = new InjectionProvider<>(implementation);

        ComponentProvider<?> provider = scope.<ComponentProvider<?>>map(s -> getScopeProvider(s,injectionProvider)).orElse(injectionProvider);
        if (qualifiers.isEmpty())
            components.put(new Component(type, null), provider);

        for (Annotation qualifier : qualifiers) {
            components.put(new Component(type, qualifier), provider);
        }
    }


    private   ComponentProvider<?> getScopeProvider(Annotation scope, ComponentProvider<?> provider) {
        return scopes.get(scope.annotationType()).apply(provider);
    }

    public<ScopeType extends Annotation> void scope(Class<ScopeType> scope, Function<ComponentProvider<?>,ComponentProvider<?>> provider) {
        scopes.put(scope, provider);
    }

    static class SingletonProvider<T> implements ComponentProvider<T>{

        private ComponentProvider<T> provider;
        private T singleton;

        public SingletonProvider(ComponentProvider<T> provider) {
            this.provider = provider;
        }

        @Override
        public T get(Context context) {
            if (singleton == null) {
                singleton = provider.get(context);
            }
            return singleton;
        }

        @Override
        public List<ComponentRef<?>> getDependencies() {
            return provider.getDependencies();
        }
    }

    public Context getContext() {
        components.keySet().forEach(component -> checkDependencies(component, new Stack<>()));
        return new Context() {

            @Override
            public <ComponentType> Optional<ComponentType> get(ComponentRef<ComponentType> ref) {
                if (ref.isContainer()) {
                    if (ref.getContainer() != Provider.class) return Optional.empty();
                    return (Optional<ComponentType>) Optional.ofNullable(getProvider(ref))
                            .map(provider -> (Provider<Object>) () -> (ComponentType) provider.get(this));
                }
                return Optional.ofNullable(getProvider(ref)).map(provider -> (ComponentType) provider.get(this));
            }

        };
    }

    private <ComponentType> ComponentProvider<?> getProvider(ComponentRef<ComponentType> componentRef) {
        return components.get(componentRef.component());
    }

    private void checkDependencies(Component component, Stack<Component> visiting) {
        for (ComponentRef dependency : components.get(component).getDependencies()) {
            if (!components.containsKey(dependency.component()))
                throw new DependencyNotFoundException(component, dependency.component());
            if (!dependency.isContainer()) {
                if (visiting.contains(dependency.component()))
                    throw new CyclicDependenciesFoundException(visiting);
                visiting.push(dependency.component());
                checkDependencies(dependency.component(), visiting);
                visiting.pop();
            }
        }
    }

    interface ComponentProvider<T> {
        T get(Context context);

        default List<ComponentRef<?>> getDependencies() {
            return of();
        }
    }

}

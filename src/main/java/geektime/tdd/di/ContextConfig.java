package geektime.tdd.di;

import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.List.of;

public class ContextConfig {

    private final Map<Component, ComponentProvider<?>> components = new HashMap<>();
    private final Map<Class<?>, ScopeProvider> scopes = new HashMap<>();

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
        Map<Class<?>, List<Annotation>> annotationGroups = Arrays.stream(annotations).collect(Collectors.groupingBy(ContextConfig::typeOf, Collectors.toList()));

        if (annotationGroups.containsKey(Illegal.class)) {
            throw new IllegalComponentException();
        }

        bind(type, annotationGroups.getOrDefault(Qualifier.class,of()),
                createScopeProvider(implementation, annotationGroups.getOrDefault(Scope.class, of())));
    }

    private <Type> ComponentProvider<?> createScopeProvider(Class<Type> implementation, List<Annotation> scopes) {
        ComponentProvider<?> injectionProvider = new InjectionProvider<>(implementation);
        return scopes.stream().findFirst().or(()-> scopeFrom(implementation))
                .<ComponentProvider<?>>map(s -> getScopeProvider(s,injectionProvider)).orElse(injectionProvider);
    }

    private <Type> void bind(Class<Type> type, List<Annotation> qualifiers, ComponentProvider<?> provider) {
        if (qualifiers.isEmpty())
            components.put(new Component(type, null), provider);
        for (Annotation qualifier : qualifiers) {
            components.put(new Component(type, qualifier), provider);
        }
    }

    private static <Type, Implementation extends Type> Optional<Annotation> scopeFrom(Class<Implementation> implementation) {
        return Arrays.stream(implementation.getAnnotations()).filter(t -> t.annotationType().isAnnotationPresent(Scope.class)).findFirst();
    }

    @interface Illegal{}

    private static Class<?> typeOf(Annotation annotation) {
        Class<? extends Annotation> type = annotation.annotationType();
        return Stream.of(Qualifier.class, Scope.class).filter(type::isAnnotationPresent).findFirst().orElse(Illegal.class);
    }

    private   ComponentProvider<?> getScopeProvider(Annotation scope, ComponentProvider<?> provider) {
        return scopes.get(scope.annotationType()).create(provider);
    }

    public<ScopeType extends Annotation> void scope(Class<ScopeType> scope, ScopeProvider provider) {
        scopes.put(scope, provider);
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

}

package geektime.tdd.di;

import jakarta.inject.Provider;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;

import static java.util.List.of;

public class ContextConfig {

    private final Map<Component, ComponentProvider<?>> components = new HashMap<>();

    public <Type> void bind(Class<Type> type, Type instance) {
        components.put(new Component(type,null), (ComponentProvider<Type>) context -> instance);
    }

    public <Type> void bind(Class<Type> type, Type instance, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers) {
            components.put(new Component(type, qualifier), (ComponentProvider<Type>) context -> instance);
        }
    }

    public <Type, Implementation extends Type> void bind(Class<Type> type, Class<Implementation> implementation) {
        components.put(new Component(type,null), new InjectionProvider<>(implementation));
    }

    public <Type, Implementation extends Type> void bind(Class<Type> type, Class<Implementation> implementation, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers) {
            components.put(new Component(type, qualifier), new InjectionProvider<>(implementation));
        }
    }

    public Context getContext() {
        components.keySet().forEach(component -> checkDependencies(component, new Stack<>()));
        return new Context() {

            @Override
            public <ComponentType> Optional<ComponentType> get(ComponentRef<ComponentType> componentRef) {
                if (componentRef.isContainerType()) {
                    if (componentRef.getContainer() != Provider.class) return Optional.empty();
                    return (Optional<ComponentType>) Optional.ofNullable(getProvider(componentRef))
                            .map(provider -> (Provider<Object>) () -> (ComponentType) provider.get(this));
                }
                return Optional.ofNullable(getProvider(componentRef)).map(provider -> (ComponentType) provider.get(this));
            }

        };
    }

    private <ComponentType> ComponentProvider<?> getProvider(ComponentRef<ComponentType> componentRef) {
        return components.get(componentRef.component());
    }

    private void checkDependencies(Component  component, Stack<Class<?>> visiting) {
        for (ComponentRef dependency : components.get(component).getDependencies()) {
            if (!components.containsKey(dependency.component())) throw new DependencyNotFoundException(component.type(), dependency.getComponentType());
            if (!dependency.isContainerType()) {
                if (visiting.contains(dependency.getComponentType())) throw new CyclicDependenciesFoundException(visiting);
                visiting.push(dependency.getComponentType());
                checkDependencies(dependency.component(), visiting);
                visiting.pop();
            }
        }
    }

    interface ComponentProvider<T> {
        T get(Context context);

        default List<ComponentRef> getDependencies() {
            return of();
        }
    }

}

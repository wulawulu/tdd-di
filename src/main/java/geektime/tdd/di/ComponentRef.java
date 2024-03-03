package geektime.tdd.di;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public class ComponentRef<ComponentType> {

    public static <ComponentType> ComponentRef<ComponentType> of(Class<ComponentType> component) {
        return new ComponentRef(component, null);
    }

    public static <ComponentType> ComponentRef<ComponentType> of(Class<ComponentType> type, Annotation qualifier) {
        return new ComponentRef(type, qualifier);
    }

    public static ComponentRef of(Type type) {
        return new ComponentRef(type, null);
    }

    public static ComponentRef of(Type type, Annotation qualifier) {
        return new ComponentRef(type, qualifier);
    }


    private Type container;

    private Component component;

    ComponentRef(Type type, Annotation qualifier) {
        init(type, qualifier);
    }

    protected ComponentRef() {
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        init(type, null);
    }

    protected ComponentRef(Annotation qualifier) {
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        init(type, qualifier);
    }

    private void init(Type type, Annotation qualifier) {
        if (type instanceof ParameterizedType containerType) {
            this.container = containerType.getRawType();
            this.component = new Component((Class<ComponentType>) containerType.getActualTypeArguments()[0], qualifier);
        } else {
            this.component = new Component((Class<ComponentType>) type, qualifier);
        }
    }

    public Type getContainer() {
        return container;
    }


    public boolean isContainer() {
        return container != null;
    }

    public Component component() {
        return component;
    }

    public Class<?> getComponentType() {
        return component.type();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentRef<?> that = (ComponentRef<?>) o;
        return Objects.equals(container, that.container) && Objects.equals(component, that.component);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, component);
    }
}

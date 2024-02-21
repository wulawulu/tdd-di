package geektime.tdd.di;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public interface Context {

    Optional get(Ref ref);

    class Ref {
        private Type container;
        private Class<?> component;

        public static Ref of(Type type) {
            if (type instanceof ParameterizedType container) return new Ref(container);
            return new Ref(((Class<?>) type));
        }

        Ref(Class<?> component) {
            this.component = component;
        }

        Ref(ParameterizedType type) {
            this.container = type.getRawType();
            this.component = (Class<?>) type.getActualTypeArguments()[0];
        }

        public boolean isContainerType() {
            return container != null;
        }

        public Type getContainer() {
            return container;
        }

        public Class<?> getComponent() {
            return component;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Ref ref = (Ref) o;
            return Objects.equals(container, ref.container) && Objects.equals(component, ref.component);
        }

        @Override
        public int hashCode() {
            return Objects.hash(container, component);
        }
    }
}

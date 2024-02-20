
package geektime.tdd.di;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ContextTest {
    ContextConfig config;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
    }

    @Nested
    public class TypeBinding {
        @Test
        public void should_bind_type_to_a_specific_instance() {
            Component instance = new Component() {
            };
            config.bind(Component.class, instance);
            Context context = config.getContext();
            assertSame(instance, context.get(Component.class).get());
        }

        @Test
        public void should_return_empty_if_component_not_defined() {
            Optional<Component> component = config.getContext().get(Component.class);
            assertTrue(component.isEmpty());
        }
    }

    @Nested
    public class DependencyCheck {

        static class ComponentWithInjectConstructor implements Component {
            private Dependency dependency;

            @Inject
            public ComponentWithInjectConstructor(Dependency dependency) {
                this.dependency = dependency;
            }

            public Dependency getDependency() {
                return dependency;
            }
        }

        static class DependencyDependedOnComponent implements Dependency {
            private Component component;

            @Inject
            public DependencyDependedOnComponent(Component component) {
                this.component = component;
            }
        }

        static class AnotherDependencyDependedOnComponent implements AnotherDependency {
            private Component component;

            @Inject
            public AnotherDependencyDependedOnComponent(Component component) {
                this.component = component;
            }
        }

        static class DependencyDependedOnAnotherDependency implements Dependency {
            private AnotherDependency anotherDependency;

            @Inject
            public DependencyDependedOnAnotherDependency(AnotherDependency anotherDependency) {
                this.anotherDependency = anotherDependency;
            }
        }

        @Test
        public void should_throw_exception_if_dependency_not_found() {
            config.bind(Component.class, ComponentWithInjectConstructor.class);
            DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
            assertEquals(Dependency.class, exception.getDependency());
            assertEquals(Component.class, exception.getComponent());
        }

        @Test
        public void should_throw_exception_if_cyclic_dependencies_found() {
            config.bind(Component.class, ComponentWithInjectConstructor.class);
            config.bind(Dependency.class, DependencyDependedOnComponent.class);
            CyclicDependenciesFoundException exception = assertThrows(CyclicDependenciesFoundException.class, () -> config.getContext());
            Set<Class<?>> classes = Sets.newSet(exception.getComponents());
            assertEquals(2, classes.size());
            assertTrue(classes.contains(Component.class));
            assertTrue(classes.contains(Dependency.class));
        }

        @Test
        public void should_throw_exception_if_transitive_cyclic_dependencies_found() {
            config.bind(Component.class, ComponentWithInjectConstructor.class);
            config.bind(Dependency.class, DependencyDependedOnAnotherDependency.class);
            config.bind(AnotherDependency.class, AnotherDependencyDependedOnComponent.class);
            CyclicDependenciesFoundException exception = assertThrows(CyclicDependenciesFoundException.class, () -> config.getContext());
            List<Class<?>> components = Arrays.asList(exception.getComponents());
            assertEquals(3, components.size());
            assertTrue(components.contains(Component.class));
            assertTrue(components.contains(Dependency.class));
            assertTrue(components.contains(AnotherDependency.class));
        }
    }
}


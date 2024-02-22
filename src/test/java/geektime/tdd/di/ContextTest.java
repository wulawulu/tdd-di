
package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.internal.util.collections.Sets;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
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
            TestComponent instance = new TestComponent() {
            };
            config.bind(TestComponent.class, instance);
            Context context = config.getContext();
            assertSame(instance, context.get(ComponentRef.of(TestComponent.class)).get());
        }

        @ParameterizedTest(name = "supporting {0}")
        @MethodSource
        public void should_bind_type_to_an_injectable_component(Class<? extends TestComponent> componentType) {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);
            config.bind(TestComponent.class, componentType);

            Optional<TestComponent> component = config.getContext().get(ComponentRef.of(TestComponent.class));

            assertTrue(component.isPresent());
            assertSame(dependency, component.get().dependency());
        }

        public static Stream<Arguments> should_bind_type_to_an_injectable_component() {
            return Stream.of(Arguments.of(Named.of("Constructor Injection", TypeBinding.ConstructorInjection.class)),
                    Arguments.of(Named.of("Filed Injection", TypeBinding.FieldInjection.class)),
                    Arguments.of(Named.of("Method Injection", TypeBinding.MethodInjection.class)));
        }

        static class ConstructorInjection implements TestComponent {
            Dependency dependency;

            @Inject
            public ConstructorInjection(Dependency dependency) {
                this.dependency = dependency;
            }

            @Override
            public Dependency dependency() {
                return dependency;
            }
        }

        static class FieldInjection implements TestComponent {
            @Inject
            Dependency dependency;
            @Override
            public Dependency dependency() {
                return dependency;
            }
        }

        static class MethodInjection implements TestComponent {
            Dependency dependency;

            @Inject
            void install(Dependency dependency) {
                this.dependency = dependency;
            }
            @Override
            public Dependency dependency() {
                return dependency;
            }
        }

        @Test
        public void should_return_empty_if_component_not_defined() {
            Context context = config.getContext();
            Optional<TestComponent> component = context.get(ComponentRef.of(TestComponent.class));
            assertTrue(component.isEmpty());
        }

        @Test
        public void should_retrieve_binded_type_as_provider() {
            TestComponent instance = new TestComponent() {
            };
            config.bind(TestComponent.class, instance);
            Context context = config.getContext();

            Provider<TestComponent> provider = context.get(new ComponentRef<Provider<TestComponent>>() {
            }).get();
            assertSame(instance, provider.get());
        }

        @Test
        public void should_not_retrieve_bind_type_as_unsupported_container() {
            TestComponent instance = new TestComponent() {
            };
            config.bind(TestComponent.class, instance);
            Context context = config.getContext();

            assertTrue(context.get(new ComponentRef<List<TestComponent>>() {
            }).isEmpty());
        }

        @Nested
        public class WithQualifier {
            @Test
            public void should_bind_instance_with_multi_qualifiers() {
                TestComponent instance = new TestComponent() {
                };
                config.bind(TestComponent.class,instance,new NamedLiteral("ChosenOne"),new SkywalkerLiteral());

                Context context = config.getContext();

                TestComponent chosenOne =context.get(ComponentRef.of(TestComponent.class, new NamedLiteral("ChosenOne"))).get();
                TestComponent skywalker =context.get(ComponentRef.of(TestComponent.class, new SkywalkerLiteral())).get();

                assertSame(instance,skywalker);
                assertSame(instance,chosenOne);
            }

            @Test
            public void should_bind_type_with_multi_qualifiers() {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency);
                config.bind(TestComponent.class, ConstructorInjection.class,new NamedLiteral("ChosenOne"),new SkywalkerLiteral());

                Context context = config.getContext();

                TestComponent chosenOne =context.get(ComponentRef.of(TestComponent.class, new NamedLiteral("ChosenOne"))).get();
                TestComponent skywalker =context.get(ComponentRef.of(TestComponent.class, new SkywalkerLiteral())).get();

                assertSame(dependency,chosenOne.dependency());
                assertSame(dependency,skywalker.dependency());
            }

            @Test
            public void should_throw_exception_if_illegal_qualifier_given_to_instance() {
                TestComponent instance = new TestComponent() {
                };
                assertThrows(IllegalComponentException.class,()->config.bind(TestComponent.class,instance,new TestLiteral()));
            }

            @Test
            public void should_throw_exception_if_illegal_qualifier_given_to_component() {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency);
                assertThrows(IllegalComponentException.class,()->config.bind(TestComponent.class, ConstructorInjection.class,new TestLiteral()));

            }
        }


    }

    @Nested
    public class DependencyCheck {

        static class TestComponentWithInjectConstructor implements TestComponent {
            private Dependency dependency;

            @Inject
            public TestComponentWithInjectConstructor(Dependency dependency) {
                this.dependency = dependency;
            }

            public Dependency getDependency() {
                return dependency;
            }
        }

        static class DependencyDependedOnComponent implements Dependency {
            private TestComponent testComponent;

            @Inject
            public DependencyDependedOnComponent(TestComponent testComponent) {
                this.testComponent = testComponent;
            }
        }

        static class AnotherDependencyDependedOnComponent implements AnotherDependency {
            private TestComponent testComponent;

            @Inject
            public AnotherDependencyDependedOnComponent(TestComponent testComponent) {
                this.testComponent = testComponent;
            }
        }

        static class DependencyDependedOnAnotherDependency implements Dependency {
            private AnotherDependency anotherDependency;

            @Inject
            public DependencyDependedOnAnotherDependency(AnotherDependency anotherDependency) {
                this.anotherDependency = anotherDependency;
            }
        }

        @ParameterizedTest
        @MethodSource
        public void should_throw_exception_if_dependency_not_found(Class<? extends TestComponent> component) {
            config.bind(TestComponent.class, component);
            DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
            assertEquals(Dependency.class, exception.getDependency());
            assertEquals(TestComponent.class, exception.getComponent());
        }

        public static Stream<Arguments> should_throw_exception_if_dependency_not_found() {
            return Stream.of(Arguments.of(Named.of("Inject Constructor", MissingDependencyConstructor.class)),
                    Arguments.of(Named.of("Inject Field", MissingDependencyFiled.class)),
                    Arguments.of(Named.of("Inject Method", MissingDependencyMethod.class)),
                    Arguments.of(Named.of("Provider in Inject Constructor", MissingDependencyProviderConstructor.class)),
                    Arguments.of(Named.of("Provider in Inject Field", MissingDependencyProviderField.class)),
                    Arguments.of(Named.of("Provider in Inject Method", MissingDependencyProviderMethod.class)));
        }

        static class MissingDependencyConstructor implements TestComponent {
            @Inject
            public MissingDependencyConstructor(Dependency dependency) {
            }
        }

        static class MissingDependencyFiled implements TestComponent {
            @Inject
            Dependency dependency;
        }

        static class MissingDependencyMethod implements TestComponent {
            @Inject
            public void install(Dependency dependency) {
            }
        }

        static class MissingDependencyProviderConstructor implements TestComponent {
            @Inject
            public MissingDependencyProviderConstructor(Provider<Dependency> dependencyProvider) {
            }
        }

        static class MissingDependencyProviderField implements TestComponent {
            @Inject
            Provider<Dependency> dependency;
        }

        static class MissingDependencyProviderMethod implements TestComponent {
            @Inject
            public void install(Provider<Dependency> dependencyProvider) {
            }
        }

        @Test
        public void should_throw_exception_if_cyclic_dependencies_found() {
            config.bind(TestComponent.class, TestComponentWithInjectConstructor.class);
            config.bind(Dependency.class, DependencyDependedOnComponent.class);
            CyclicDependenciesFoundException exception = assertThrows(CyclicDependenciesFoundException.class, () -> config.getContext());
            Set<Class<?>> classes = Sets.newSet(exception.getComponents());
            assertEquals(2, classes.size());
            assertTrue(classes.contains(TestComponent.class));
            assertTrue(classes.contains(Dependency.class));
        }

        @Test
        public void should_throw_exception_if_transitive_cyclic_dependencies_found() {
            config.bind(TestComponent.class, TestComponentWithInjectConstructor.class);
            config.bind(Dependency.class, DependencyDependedOnAnotherDependency.class);
            config.bind(AnotherDependency.class, AnotherDependencyDependedOnComponent.class);
            CyclicDependenciesFoundException exception = assertThrows(CyclicDependenciesFoundException.class, () -> config.getContext());
            List<Class<?>> components = Arrays.asList(exception.getComponents());
            assertEquals(3, components.size());
            assertTrue(components.contains(TestComponent.class));
            assertTrue(components.contains(Dependency.class));
            assertTrue(components.contains(AnotherDependency.class));
        }

        static class CyclicDependencyProviderConstructor implements Dependency {
            @Inject
            public CyclicDependencyProviderConstructor(Provider<TestComponent> componentProvider) {
            }
        }

        @Test
        public void should_not_throw_exception_if_cyclic_dependency_via_provider() {
            config.bind(TestComponent.class, TestComponentWithInjectConstructor.class);
            config.bind(Dependency.class, CyclicDependencyProviderConstructor.class);

            Context context = config.getContext();
            assertTrue(context.get(ComponentRef.of(TestComponent.class)).isPresent());
        }

        @Nested
        public class WithQualifier {
            //TODO dependency missing  if qualifier not match
            //TODO check cyclic dependencies with qualifier
        }
    }
}


record NamedLiteral(String value) implements jakarta.inject.Named{
    @Override
    public Class<? extends Annotation> annotationType() {
        return jakarta.inject.Named.class;
    }
}

@Documented
@Retention(RUNTIME)
@Qualifier
@interface Skywalker {
}

record SkywalkerLiteral() implements Skywalker {
    @Override
    public Class<? extends Annotation> annotationType() {
        return Skywalker.class;
    }
}

record TestLiteral() implements Test {
    @Override
    public Class<? extends Annotation> annotationType() {
        return Test.class;
    }
}
package geektime.tdd.di;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Nested
public class InjectionTest {
    private Dependency dependency = mock(Dependency.class);
    private Context context = mock(Context.class);

    @BeforeEach
    public void setup() {
        when(context.get(eq(Dependency.class))).thenReturn(Optional.of(dependency));
    }

    @Nested
    public class ConstructorInjection {

        @Nested
        class Injection {

            static class DefaultConstructor implements Component {
                public DefaultConstructor() {
                }
            }

            @Test
            public void should_call_default_constructor_if_no_inject_constructor() {
                DefaultConstructor instance = new ConstructorInjectionProvider<>(DefaultConstructor.class).get(context);

                assertNotNull(instance);
            }

            static class InjectConstructor {
                Dependency dependency;

                @Inject
                public InjectConstructor(Dependency dependency) {
                    this.dependency = dependency;
                }
            }


            @Test
            public void should_inject_dependency_via_inject_constructor() {
                InjectConstructor instance = new ConstructorInjectionProvider<>(InjectConstructor.class).get(context);
                assertSame(dependency, instance.dependency);
            }

            @Test
            public void should_include_dependency_from_inject_constructor() {
                ConstructorInjectionProvider<InjectConstructor> provider = new ConstructorInjectionProvider<>(InjectConstructor.class);
                assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray());
            }
        }

        @Nested
        class IllegalInjectConstructors {

            static abstract class AbstractComponentClass implements Component {
                public AbstractComponentClass() {
                }
            }

            @Test
            public void should_throw_error_if_component_is_abstract() {
                assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(AbstractComponentClass.class));
            }

            @Test
            public void should_throw_error_if_component_is_interface() {
                assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(Component.class));
            }

            static class MultiInjectConstructors implements Component {
                @Inject public MultiInjectConstructors(AnotherDependency dependency) { }
                @Inject public MultiInjectConstructors(Dependency dependency) { }
            }

            @Test
            public void should_throw_exception_if_multi_inject_constructors_provided() {
                assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(MultiInjectConstructors.class));
            }

            class NoInjectNorDefaultConstructor implements Component {
                public NoInjectNorDefaultConstructor(String name) {
                }
            }

            @Test
            public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
                assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(NoInjectNorDefaultConstructor.class));
            }

        }


    }

    @Nested
    public class FieldInjection {

        @Nested
        class Injection {
            static class ComponentWithFieldInjection implements Component {
                @Inject
                Dependency dependency;
            }

            static class SubclassWithFieldInjection extends ComponentWithFieldInjection {
            }

            @Test
            public void should_inject_dependency_via_field() {
                ComponentWithFieldInjection instance = new ConstructorInjectionProvider<>(ComponentWithFieldInjection.class).get(context);
                assertSame(dependency, instance.dependency);
            }

            @Test
            public void should_inject_dependency_via_superclass_inject_field() {

                ComponentWithFieldInjection instance = new ConstructorInjectionProvider<>(SubclassWithFieldInjection.class).get(context);
                assertSame(dependency, instance.dependency);
            }

            @Test
            public void should_include_dependency_from_filed_dependencies() {
                ConstructorInjectionProvider<Injection.ComponentWithFieldInjection> provider = new ConstructorInjectionProvider<>(ComponentWithFieldInjection.class);
                assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
            }
        }

        @Nested
        class IllegalInjectFields {

            static class FinalInjectClass implements Component {
                @Inject
                final Dependency dependency = null;
            }

            @Test
            public void should_throw_exception_if_component_filed_is_final() {
                assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(FinalInjectClass.class));
            }

        }
    }

    @Nested
    public class MethodInjection {
        @Nested
        class Injection {
            static class InjectMethodWithNoDependency {
                boolean called = false;

                @Inject
                void install() {
                    this.called = true;
                }
            }

            @Test
            public void should_call_inject_method_even_if_no_dependency_declared() {
                InjectMethodWithNoDependency instance = new ConstructorInjectionProvider<>(InjectMethodWithNoDependency.class).get(context);
                assertTrue(instance.called);
            }

            static class InjectMethodWithDependency {
                Dependency dependency;

                @Inject
                void install(Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_via_inject_method() {
                InjectMethodWithDependency component = new ConstructorInjectionProvider<>(InjectMethodWithDependency.class).get(context);
                assertSame(dependency, component.dependency);
            }

            static class SuperClassWithInjectMethod {
                int superCalled = 0;

                @Inject
                void install() {
                    superCalled++;
                }
            }

            static class SubclassWithInjectMethod extends SuperClassWithInjectMethod {
                int subCalled = 0;

                @Inject
                void installAnother() {
                    subCalled = superCalled + 1;
                }
            }

            @Test
            public void should_inject_dependencies_via_inject_method_from_superclass() {
                SubclassWithInjectMethod component = new ConstructorInjectionProvider<>(SubclassWithInjectMethod.class).get(context);

                assertEquals(1, component.superCalled);
                assertEquals(2, component.subCalled);
            }

            static class SubclassOverrideSuperClassWithInject extends SuperClassWithInjectMethod {
                @Inject
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
                SubclassOverrideSuperClassWithInject component = new ConstructorInjectionProvider<>(SubclassOverrideSuperClassWithInject.class).get(context);

                assertEquals(1, component.superCalled);
            }

            static class SubclassOverrideSuperClassWithNoInject extends SuperClassWithInjectMethod {
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_not_call_inject_method_if_override_with_no_inject() {

                SubclassOverrideSuperClassWithNoInject component = new ConstructorInjectionProvider<>(SubclassOverrideSuperClassWithNoInject.class).get(context);

                assertEquals(0, component.superCalled);
            }

            @Test
            public void should_include_dependencies_from_inject_method() {
                ConstructorInjectionProvider<InjectMethodWithDependency> provider = new ConstructorInjectionProvider<>(InjectMethodWithDependency.class);
                assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
            }
        }

        @Nested
        class IllegalInjectMethod{

            static class ParameterDefinedClass {
                @Inject
                <T> void install(T t) {
                }
            }

            @Test
            public void should_throw_exception_if_type_parameter_defined() {
                assertThrows(IllegalComponentException.class, () -> {
                    new ConstructorInjectionProvider<>(ParameterDefinedClass.class);
                });
            }
        }
    }
}

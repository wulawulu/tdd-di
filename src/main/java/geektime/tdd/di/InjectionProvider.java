package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

public class InjectionProvider<T> implements ComponentProvider<T> {

    private Injectable<Constructor<T>> injectConstructor;
    private List<Injectable<Method>> injectMethods;
    private List<Injectable<Field>> injectFileds;

    public InjectionProvider(Class<T> component) {
        if (Modifier.isAbstract(component.getModifiers())) throw new IllegalComponentException();

        this.injectConstructor = getInjectionConstructor(component);
        this.injectMethods = getInjectMethod(component);
        this.injectFileds = getInjectionFiled(component);

        if (injectFileds.stream().map(Injectable::element).anyMatch(filed -> Modifier.isFinal(filed.getModifiers())))
            throw new IllegalComponentException();
        if (injectMethods.stream().map(Injectable::element).anyMatch(method -> method.getTypeParameters().length != 0))
            throw new IllegalComponentException();

    }

    @Override
    public T get(Context context) {
        try {
            T instance = injectConstructor.element.newInstance(injectConstructor.toDependencies(context));
            for (Injectable<Field> field : injectFileds) {
                field.element.set(instance, field.toDependencies(context)[0]);
            }
            for (Injectable<Method> method : injectMethods) {
                method.element.invoke(instance, method.toDependencies(context));
            }
            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException exception) {
            throw new RuntimeException();
        }
    }

    @Override
    public List<ComponentRef<?>> getDependencies() {
        return concat(concat(Stream.of(injectConstructor), injectFileds.stream()), injectMethods.stream())
                .flatMap(i -> stream(i.required)).toList();
    }

    record Injectable<Element extends AccessibleObject>(Element element, ComponentRef<?>[] required) {
        static <Element extends Executable> Injectable<Element> of(Element constructor) {
            return new Injectable<>(constructor, stream(constructor.getParameters()).map(Injectable::toComponentRef).toArray(ComponentRef<?>[]::new));
        }

        static  Injectable<Field> of(Field field) {
            return new Injectable<>(field, new ComponentRef<?>[]{toComponentRef(field)});
        }

        Object[] toDependencies(Context context) {
            return stream(required).map(context::get).map(Optional::get).toArray();
        }

        private static ComponentRef<?> toComponentRef(Field f) {
            return ComponentRef.of(f.getGenericType(), getQualifier(f));
        }

        private static ComponentRef<?> toComponentRef(Parameter p) {
            return ComponentRef.of(p.getParameterizedType(), getQualifier(p));
        }

        private static Annotation getQualifier(AnnotatedElement element) {
            List<Annotation> qualifiers = stream(element.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(Qualifier.class)).toList();
            if (qualifiers.size() > 1) {
                throw new IllegalComponentException();
            }
            return qualifiers.stream().findFirst().orElse(null);
        }
    }

    private static <T> Injectable<Constructor<T>> getInjectionConstructor(Class<T> component) {
        List<Constructor<?>> injectConstructors = injectable(component.getConstructors()).toList();
        if (injectConstructors.size() > 1) throw new IllegalComponentException();
        return Injectable.of((Constructor<T>) injectConstructors.stream().findFirst().orElseGet(() -> defualtConstructor(component)));
    }

    private static List<Injectable<Field>> getInjectionFiled(Class<?> component) {
        List<Field> injectFields = traverse(component, (fields, current) -> injectable(current.getDeclaredFields()).toList());
        return injectFields.stream().map(Injectable::of).toList();
    }

    private static List<Injectable<Method>> getInjectMethod(Class<?> component) {
        List<Method> injectMethods = traverse(component, (methods, current) -> injectable(current.getDeclaredMethods())
                .filter(m -> isOverrideByInjectMethod(m, methods))
                .filter(m -> isOverrideByNoInjectMethod(component, m)).toList());
        Collections.reverse(injectMethods);
        return injectMethods.stream().map(Injectable::of).toList();
    }

    private static <Type> Constructor<Type> defualtConstructor(Class<Type> implementation) {
        try {
            return implementation.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalComponentException();
        }
    }

    private static <T> List<T> traverse(Class<?> component, BiFunction<List<T>, Class<?>, List<T>> finder) {
        List<T> members = new ArrayList<>();
        Class<?> current = component;
        while (current != Object.class) {
            members.addAll(finder.apply(members, current));
            current = current.getSuperclass();
        }
        return members;
    }

    private static <T extends AnnotatedElement> Stream<T> injectable(T[] declaredFields) {
        return stream(declaredFields).filter(f -> f.isAnnotationPresent(Inject.class));
    }

    private static boolean isOverride(Method m, Method o) {
        return o.getName().equals(m.getName()) &&
                Arrays.equals(o.getParameterTypes(), m.getParameterTypes());
    }

    private static <T> boolean isOverrideByNoInjectMethod(Class<T> component, Method m) {
        return stream(component.getDeclaredMethods()).filter(m1 -> !m1.isAnnotationPresent(Inject.class))
                .noneMatch(o -> isOverride(m, o));
    }

    private static boolean isOverrideByInjectMethod(Method m, List<Method> injectMethods) {
        return injectMethods.stream().noneMatch(o -> isOverride(m, o));
    }

}


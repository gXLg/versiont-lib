package dev.gxlg.versiont.api;

import dev.gxlg.versiont.api.types.RedirectedCall;
import dev.gxlg.versiont.api.types.StoredField;
import dev.gxlg.versiont.api.types.StoredMethod;
import dev.gxlg.versiont.api.types.WrappedMethod;
import dev.gxlg.versiont.api.types.Wrapper;
import dev.gxlg.versiont.api.types.WrapperInterface;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings({ "unused" })
public class R {
    private static final Map<ClassLoader, Map<Integer, Class<?>>> clazzCache = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<ClassLoader, Map<Integer, MethodHandle>> constructorsCache = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<ClassLoader, Map<Integer, StoredMethod>> methodsCache = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<ClassLoader, Map<Integer, StoredField>> fieldsCache = Collections.synchronizedMap(new WeakHashMap<>());

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodType CONSTRUCTOR_TYPE = MethodType.methodType(Object.class, Object[].class);

    private static final Map<Class<?>, List<Class<?>>> userSubClazzes = new ConcurrentHashMap<>();

    private static final List<Class<?>> userClazzes = Collections.synchronizedList(new ArrayList<>());

    private static final List<Class<?>> actualUserClazzes = Collections.synchronizedList(new ArrayList<>());

    private static <T> T cache(Map<ClassLoader, Map<Integer, T>> cache, Class<?> base, Class<?>[] types, String[] names, Supplier<T> supplier) {
        Map<Integer, T> cacheMap = cache.computeIfAbsent(Thread.currentThread().getContextClassLoader(), cl -> new HashMap<>());
        return cacheMap.computeIfAbsent(Objects.hash(base, Arrays.hashCode(types), Arrays.hashCode(names)), i -> supplier.get());
    }

    public static RClass clz(String names) {
        return new RClass(names);
    }

    public static RClass clz(Class<?> clz) {
        return new RClass(clz);
    }

    @SuppressWarnings("unchecked")
    public static <T> Function<Object, T[]> arrayWrapper(Function<Object, T> wrapperT) {
        return obj -> (T[]) Stream.of((Object[]) obj).map(wrapperT).toArray();
    }

    public static <T> Function<T[], Object> arrayUnwrapper(Function<T, Object> unwrapperT) {
        return wrap -> Stream.of(wrap).map(unwrapperT).toArray();
    }

    public static <T, R> Function<T, R> nullSafe(Function<T, R> function) {
        return obj -> obj == null ? null : function.apply(obj);
    }

    public static boolean methodMatches(Method method, Class<?>... types) {
        Class<?> returnType = types[0];
        Class<?>[] methodParams = method.getParameterTypes();
        if (types.length - 1 != methodParams.length) {
            return false;
        }
        if (!returnType.isAssignableFrom(method.getReturnType())) {
            return false;
        }
        for (int i = 0; i < types.length - 1; i++) {
            if (!methodParams[i].isAssignableFrom(types[i + 1])) {
                return false;
            }
        }
        return true;
    }

    public static boolean fieldMatches(Field field, Class<?> fieldType) {
        return fieldType.isAssignableFrom(field.getType());
    }

    public static Object unwrapWrapper(Wrapper<?> wrapper) {
        return wrapper == null ? null : wrapper.unwrap();
    }

    public static <T> T unwrapWrapper(Wrapper<?> wrapper, Class<T> clz) {
        return wrapper == null ? null : wrapper.unwrap(clz);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Wrapper<?>> T wrapperInst(Class<T> wrapperClass, Object instance) {
        if (instance == null) {
            return null;
        }
        Class<?> current = instance.getClass();

        // try internal subclasses first
        boolean userSubClass = isUserClass(wrapperClass);
        if (!userSubClass) {
            List<?> subClazzes = ((List<?>) clz(wrapperClass).fld("subClazzes", List.class).get());
            for (Object _subClazz : subClazzes) {
                Class<? extends T> subClazz = (Class<? extends T>) _subClazz;
                try {
                    Class<?> actualSubClass = ((R.RClass) R.clz(subClazz).fld("clazz", R.RClass.class).get()).self();
                    if (actualSubClass.isAssignableFrom(current)) {
                        return wrapperInst(subClazz, instance);
                    }
                } catch (Exception ignored) {
                    // some classes might not be found due to version differences, just ignore them
                }
            }
        }

        // then try user subclasses, which can be added at runtime
        for (Class<?> _subClazz : userSubClazzes.getOrDefault(wrapperClass, List.of())) {
            Class<? extends T> subClazz = (Class<? extends T>) _subClazz;
            Class<?> actualSubClass = ((RClass) clz(subClazz).fld("clazz", RClass.class).get()).self();
            if (actualSubClass.isAssignableFrom(current)) {
                return wrapperInst(subClazz, instance);
            }
        }

        if (userSubClass) {
            Class<?> actualSubClass = ((RClass) clz(wrapperClass).fld("clazz", RClass.class).get()).self();
            return wrapperClass.cast(clz(actualSubClass).inst(instance).fld("__wrapper", Wrapper.class).get());
        }

        // finally, if no subclass matches, use wrapper constructor
        return wrapperClass.cast(clz(wrapperClass).constr(Wrapper.DelayedConstructor.class).newInst((Wrapper.DelayedConstructor) clz -> instance).self());
    }

    private static Object intercept(Method method, Class<?> wrapperClass, Object wrapper, Object[] args, Callable<?> superCall) throws Exception {
        if (wrapperClass == Wrapper.class) {
            return superCall.call();
        }
        for (WrappedMethod wrappedMethod : getWrappedMethods(wrapperClass)) {
            if (wrappedMethod.matches(method)) {
                return wrappedMethod.invoke(wrapper, args);
            }
        }
        for (Class<?> iface : wrapperClass.getInterfaces()) {
            RedirectedCall redirect = interceptInterface(method, iface, wrapper, args);
            if (redirect.isRedirected()) {
                return redirect.result();
            }
        }
        return intercept(method, wrapperClass.getSuperclass(), wrapper, args, superCall);
    }

    private static RedirectedCall interceptInterface(Method method, Class<?> wrapperClass, Object wrapper, Object[] args) throws Exception {
        for (WrappedMethod wrappedMethod : getWrappedMethods(wrapperClass)) {
            if (wrappedMethod.matches(method)) {
                return new RedirectedCall(true, wrappedMethod.invoke(wrapper, args));
            }
        }
        for (Class<?> iface : wrapperClass.getInterfaces()) {
            RedirectedCall redirect = interceptInterface(method, iface, wrapper, args);
            if (redirect.isRedirected()) {
                return redirect;
            }
        }
        return new RedirectedCall(false, null);
    }

    public static <T extends Wrapper<?>> T interfaceInstance(Class<? extends WrapperInterface> wrapperInterface, Class<T> wrapperClass) {
        Class<?> implClz = ((RClass) clz(wrapperClass).fld("clazz", RClass.class).get()).self();
        return wrapperInst(
            wrapperClass, Proxy.newProxyInstance(
                implClz.getClassLoader(), new Class[]{ implClz }, (proxy, method, args) -> {
                    RedirectedCall redirect = interceptInterface(method, wrapperInterface, proxy, args);
                    if (redirect.isRedirected()) {
                        return redirect.result();
                    }
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
            )
        );
    }

    private static List<WrappedMethod> getWrappedMethods(Class<?> wrapperClass) {
        if (userClazzes.contains(wrapperClass)) {
            return List.of();
        }
        List<WrappedMethod> wrappedMethods = new ArrayList<>();
        List<?> _wrappedMethods = ((List<?>) clz(wrapperClass).fld("wrappedMethods", List.class).get());
        for (Object _wrappedMethod : _wrappedMethods) {
            wrappedMethods.add((WrappedMethod) _wrappedMethod);
        }
        return wrappedMethods;
    }

    @SafeVarargs
    @SuppressWarnings("resource")
    public static <T extends Wrapper<?>> RClass extendWrapper(Class<T> superClass, Class<? extends T> extendingWrapper, Class<? extends WrapperInterface>... implementingInterfaces) {
        userSubClazzes.computeIfAbsent(superClass, c -> Collections.synchronizedList(new ArrayList<>())).add(extendingWrapper);
        userClazzes.add(extendingWrapper);
        return new RClass(() -> {
            try {
                Class<?> superClz = ((RClass) clz(superClass).fld("clazz", RClass.class).get()).self();
                Class<?>[] interfaces = new Class[implementingInterfaces.length];
                for (int i = 0; i < implementingInterfaces.length; i++) {
                    interfaces[i] = ((RClass) clz(implementingInterfaces[i]).fld("clazz", RClass.class).get()).self();
                }

                DynamicType.Unloaded<?> unloaded = new ByteBuddy().subclass(superClz).implement(interfaces).name(extendingWrapper.getName() + "Impl")
                                                                  .defineField("__wrapper", extendingWrapper, Visibility.PUBLIC)
                                                                  .method(ElementMatchers.isVirtual().and(ElementMatchers.not(ElementMatchers.isFinalizer())))
                                                                  .intercept(MethodDelegation.to(Interceptor.class)).make();
                Class<?> actualClass = unloaded.load(superClz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
                actualUserClazzes.add(actualClass);
                return actualClass;
            } catch (Exception e) {
                throw new RuntimeException("Failed to extend class", e);
            }
        });
    }

    public static void preload(RClass... classes) {
        CompletableFuture.runAsync(
            () -> {
                for (RClass clz : classes) {
                    clz.self();
                }
            }, Executors.newSingleThreadExecutor()
        );
    }

    public static boolean isUserClass(Class<?> clz) {
        return userClazzes.contains(clz);
    }

    public static boolean isActualUserClass(Class<?> clz) {
        return actualUserClazzes.contains(clz);
    }

    private static MethodHandle findMethodBetween(Class<?> lowestClass, Class<?> highestClass, String[] methodNames, Class<?> rtype, Class<?>[] ptypes) {
        if (!isActualUserClass(lowestClass)) {
            for (String name : methodNames) {
                try {
                    return LOOKUP.findSpecial(lowestClass, name, MethodType.methodType(rtype, ptypes), lowestClass).withVarargs(false);
                } catch (NoSuchMethodException ignored) {
                } catch (IllegalAccessException ignored) {
                    try {
                        return MethodHandles.privateLookupIn(lowestClass, LOOKUP).findSpecial(lowestClass, name, MethodType.methodType(rtype, ptypes), lowestClass).withVarargs(false);
                    } catch (IllegalAccessException | NoSuchMethodException ignored2) {
                    }
                }
            }
        }
        List<Class<?>> classesAbove = new ArrayList<>();
        if (highestClass.isAssignableFrom(lowestClass.getSuperclass())) {
            classesAbove.add(lowestClass.getSuperclass());
        }
        for (Class<?> iface : lowestClass.getInterfaces()) {
            if (highestClass.isAssignableFrom(iface)) {
                classesAbove.add(iface);
            }
        }
        for (Class<?> classAbove : classesAbove) {
            try {
                return findMethodBetween(classAbove, highestClass, methodNames, rtype, ptypes);
            } catch (RuntimeException ignored) {
            }
        }
        throw new RuntimeException(
            "Instance method not found from " + Arrays.toString(methodNames) + " between " + lowestClass + " and " + highestClass + " with signature " + Arrays.toString(ptypes) + " -> " + rtype);
    }

    public static class Interceptor {
        @RuntimeType
        public static Object intercept(@Origin Method method, @FieldValue("__wrapper") Wrapper<?> wrapper, @AllArguments Object[] args, @SuperCall Callable<?> superCall) throws Exception {
            return R.intercept(method, wrapper.getClass(), wrapper, args, superCall);
        }

        @RuntimeType
        public static Object interceptAbstract(@Origin Method method, @FieldValue("__wrapper") Wrapper<?> wrapper, @AllArguments Object[] args) throws Exception {
            RedirectedCall redirect = interceptInterface(method, wrapper.getClass(), wrapper, args);
            if (redirect.isRedirected()) {
                return redirect.result();
            }
            throw new RuntimeException("Non-intercepted call to abstract method: " + method);
        }
    }

    public static class RClass {
        private final Supplier<Class<?>> lazyClz;

        private final AtomicReference<Class<?>> clz = new AtomicReference<>();

        private RClass(String names) {
            lazyClz = () -> {
                String[] classNames = names.split("/");
                return cache(
                    clazzCache, null, new Class[0], classNames, () -> {
                        for (String clazz : classNames) {
                            try {
                                return Class.forName(clazz);
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                        throw new RuntimeException("Class not found from " + Arrays.toString(classNames));
                    }
                );
            };
        }

        private RClass(Supplier<Class<?>> loader) {
            lazyClz = loader;
        }

        private RClass(Class<?> clz) {
            lazyClz = () -> clz;
        }

        public RInstance inst(Object inst) {
            try {
                return new RInstance(self(), self().cast(inst));
            } catch (ClassCastException e) {
                throw new RuntimeException("Object is not of type " + self().getName() + ", instead: " + inst.getClass().getName());
            }
        }

        public RConstructor constr(Class<?>... types) {
            return new RConstructor(self(), types);
        }

        public RField fld(String names, Class<?> type) {
            return new RField(null, names, self(), type);
        }

        public RMethod mthd(String names, Class<?>... types) {
            return new RMethod(null, names, self(), types);
        }

        public Class<?> self() {
            return clz.updateAndGet(clz -> {
                if (clz == null) {
                    return lazyClz.get();
                }
                return clz;
            });
        }

        public RClass arrayType() {
            return clz(self().arrayType());
        }
    }

    public static class RInstance {
        private final Class<?> clz;

        private final Object inst;

        private RInstance(Class<?> clz, Object inst) {
            this.clz = clz;
            this.inst = inst;
        }

        public RField fld(String names, Class<?> type) {
            return new RField(inst, names, clz, type);
        }

        public RMethod mthd(String names, Class<?>... types) {
            return new RMethod(inst, names, clz, types);
        }

        public Object self() {
            return inst;
        }
    }

    public static class RMethod {
        private final Object inst;

        private final Supplier<StoredMethod> lazyMethod;

        private final AtomicReference<StoredMethod> method = new AtomicReference<>();

        public RMethod(Object inst, String names, Class<?> clz, Class<?>[] types) {
            this.inst = inst;
            String[] methodNames = names.split("/");
            this.lazyMethod = () -> cache(
                methodsCache, clz, types, methodNames, () -> {
                    Class<?>[] ptypes = Arrays.copyOfRange(types, 1, types.length);
                    if (inst != null) {
                        // instance method
                        return StoredMethod.of(ptypes.length, true, findMethodBetween(inst.getClass(), clz, methodNames, types[0], ptypes));
                    } else {
                        // static method
                        for (String name : methodNames) {
                            try {
                                MethodHandle handle = LOOKUP.findStatic(clz, name, MethodType.methodType(types[0], ptypes)).withVarargs(false);
                                return StoredMethod.of(ptypes.length, false, handle);
                            } catch (NoSuchMethodException ignored) {
                            } catch (IllegalAccessException ignored) {
                                try {
                                    MethodHandle handle = MethodHandles.privateLookupIn(clz, LOOKUP).findStatic(clz, name, MethodType.methodType(types[0], ptypes)).withVarargs(false);
                                    return StoredMethod.of(ptypes.length, false, handle);
                                } catch (IllegalAccessException | NoSuchMethodException ignored2) {
                                }
                            }
                        }
                        throw new RuntimeException("Static method not found from " + Arrays.toString(methodNames) + " for " + clz + " with signature " + Arrays.toString(ptypes) + " -> " + types[0]);
                    }
                }
            );
        }

        public Object invk(Object... args) throws Throwable {
            return self().invk(inst, args);
        }

        public StoredMethod self() {
            return method.updateAndGet(m -> {
                if (m == null) {
                    return lazyMethod.get();
                }
                return m;
            });
        }
    }

    public static class RField {
        private final Object inst;

        private final Supplier<StoredField> lazyField;

        private final AtomicReference<StoredField> fld = new AtomicReference<>();

        public RField(Object inst, String names, Class<?> clz, Class<?> fieldType) {
            this.inst = inst;
            String[] fieldNames = names.split("/");
            this.lazyField = () -> cache(
                fieldsCache, clz, new Class[]{ fieldType }, fieldNames, () -> {
                    Set<String> nameSet = Set.of(fieldNames);
                    for (Field field : clz.getDeclaredFields()) {
                        if (nameSet.contains(field.getName()) && fieldMatches(field, fieldType)) {
                            try {
                                if (!field.trySetAccessible()) {
                                    continue;
                                }
                                return StoredField.of(inst != null, LOOKUP.unreflectVarHandle(field));
                            } catch (IllegalAccessException ignored) {
                                try {
                                    return StoredField.of(inst != null, MethodHandles.privateLookupIn(clz, LOOKUP).unreflectVarHandle(field));
                                } catch (IllegalAccessException ignored2) {
                                }
                            }
                        }
                    }
                    throw new RuntimeException("Field not found from " + Arrays.toString(fieldNames) + " for " + clz + " of type " + fieldType);
                }
            );
        }

        public void set(Object value) {
            self().set(inst, value);
        }

        public Object get() {
            return self().get(inst);
        }

        public StoredField self() {
            return fld.updateAndGet(fld -> {
                if (fld == null) {
                    return lazyField.get();
                }
                return fld;
            });
        }
    }

    public static class RConstructor {
        private final Class<?> clz;

        private final Supplier<MethodHandle> lazyConstr;

        private final AtomicReference<MethodHandle> constr = new AtomicReference<>();

        private RConstructor(Class<?> clz, Class<?>... types) {
            this.clz = clz;
            this.lazyConstr = () -> cache(
                constructorsCache, clz, types, new String[0], () -> {
                    try {
                        Constructor<?> c = clz.getDeclaredConstructor(types);
                        c.setAccessible(true);
                        MethodHandle handle = LOOKUP.unreflectConstructor(c).withVarargs(false);
                        return handle.asSpreader(Object[].class, c.getParameterCount()).asType(CONSTRUCTOR_TYPE);
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                        throw new RuntimeException("Constructor not found for " + clz + " with args " + Arrays.toString(types));
                    }
                }
            );
        }

        public RInstance newInst(Object... args) {
            try {
                return new RInstance(clz, self().invokeExact(args));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public MethodHandle self() {
            return constr.updateAndGet(constr -> {
                if (constr == null) {
                    return lazyConstr.get();
                }
                return constr;
            });
        }
    }

}

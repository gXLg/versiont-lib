package dev.gxlg.versiont.api;

import dev.gxlg.versiont.api.types.StoredField;
import dev.gxlg.versiont.api.types.StoredMethod;
import dev.gxlg.versiont.api.types.Wrapper;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
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

    @SuppressWarnings("resource")
    public static <T extends Wrapper<?>> RClass extendWrapper(Class<T> superClass, Class<? extends T> extendingWrapper) {
        R.clz(List.class).inst(clz(superClass).fld("userSubClazzes", List.class).get()).mthd("add", boolean.class, Object.class).invk(extendingWrapper);
        return new RClass(() -> {
            try {
                Class<?> superClz = ((RClass) clz(superClass).fld("clazz", RClass.class).get()).self();
                Class<?> intercept = superClass.getDeclaredClasses()[0];
                DynamicType.Unloaded<?> unloaded = new ByteBuddy().subclass(superClz).name(extendingWrapper.getName() + "Impl").defineField("__wrapper", extendingWrapper, Visibility.PUBLIC)
                                                                  .method(ElementMatchers.isVirtual().and(ElementMatchers.not(ElementMatchers.isFinalizer()))).intercept(MethodDelegation.to(intercept))
                                                                  .make();
                return unloaded.load(superClz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
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
                    Set<String> nameSet = Set.of(methodNames);
                    for (Method method : clz.getDeclaredMethods()) {
                        if (nameSet.contains(method.getName()) && methodMatches(method, types)) {
                            try {
                                method.setAccessible(true);
                                MethodHandle handle = LOOKUP.unreflect(method);
                                if (method.isVarArgs()) {
                                    handle = handle.asFixedArity();
                                }
                                return StoredMethod.of(method.getParameterCount(), inst != null, handle);
                            } catch (IllegalAccessException ignored) {
                            }
                        }
                    }
                    throw new RuntimeException("Method not found from " + Arrays.toString(methodNames) + " for " + clz + " with params " + Arrays.toString(types));
                }
            );
        }

        public Object invk(Object... args) {
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
                                field.setAccessible(true);
                                return StoredField.of(inst != null, LOOKUP.unreflectVarHandle(field));
                            } catch (IllegalAccessException ignored) {
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
                        MethodHandle handle = LOOKUP.unreflectConstructor(c);
                        if (c.isVarArgs()) {
                            handle = handle.asFixedArity();
                        }
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

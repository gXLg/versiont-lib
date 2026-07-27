package dev.gxlg.versiont.api.types;

import dev.gxlg.versiont.api.R;

import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public abstract class Wrapper<S extends Wrapper<S>> {
    protected final Object instance;

    @SuppressWarnings("unchecked")
    protected Wrapper(DelayedConstructor delayedConstructor) {
        synchronized (lock) {
            Class<?> wrapperClass = this.getClass();
            R.RClass actualClass = (R.RClass) R.clz(wrapperClass).fld("clazz", R.clz(R.RClass.class)).get();

            __preInitWrapper = this;
            Object instance = delayedConstructor.construct(actualClass);
            if (instance == null) {
                throw new RuntimeException("Cannot wrap null instance");
            }
            this.instance = instance;
            __preInitWrapper = null;

            // handling user classes
            // - setting "__wrapper" field on the class and all superclasses
            Class<?> currentClass = wrapperClass;
            R.RClass currentActualClass = actualClass;
            do {
                if (!R.isUserClass(currentClass)) {
                    break;
                }

                currentActualClass.inst(instance).fld("__wrapper", R.clz(currentClass)).set(this);
                currentClass = currentClass.getSuperclass();
                currentActualClass = (R.RClass) R.clz(currentClass).fld("clazz", R.clz(R.RClass.class)).get();

            } while (currentClass != null);

            // saving interface instances
            if (this instanceof WrapperInterface thisIface) {
                for (Class<?> iface : wrapperClass.getInterfaces()) {
                    if (WrapperInterface.class.isAssignableFrom(iface)) {
                        Class<?> ifaceWrapperClass = (Class<?>) R.clz(iface).fld("wrapper", R.clz(Class.class)).get();
                        Map<WrapperInterface, Wrapper<?>> instances = (Map<WrapperInterface, Wrapper<?>>) R.clz(iface).fld("instances", R.clz(Map.class)).get();
                        instances.put(thisIface, R.wrapperInst((Class<Wrapper<?>>) ifaceWrapperClass, instance));
                    }
                }
            }
        }
    }

    public Object unwrap() {
        return instance;
    }

    public <T> T unwrap(Class<T> clz) {
        return clz.cast(instance);
    }

    @Override
    public boolean equals(Object that) {
        if (that == null) {
            return false;
        }
        if (!(that instanceof Wrapper<?> wrapper)) {
            return false;
        }
        return Objects.equals(instance, wrapper.instance);
    }

    private final static Object lock = new Object();

    public static Wrapper<?> __preInitWrapper = null;

    public interface DelayedConstructor {
        Object construct(R.RClass actualClass);
    }
}

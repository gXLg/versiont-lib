package dev.gxlg.versiont.api.types;

import dev.gxlg.versiont.api.R;

import java.util.Map;
import java.util.Objects;

public abstract class Wrapper<S extends Wrapper<S>> {
    protected final Object instance;

    @SuppressWarnings("unchecked")
    protected Wrapper(DelayedConstructor delayedConstructor) {
        Class<?> wrapperClass = this.getClass();
        R.RClass actualClass = (R.RClass) R.clz(wrapperClass).fld("clazz", R.RClass.class).get();

        Object instance = delayedConstructor.construct(actualClass);
        if (instance == null) {
            throw new RuntimeException("Cannot wrap null instance");
        }
        this.instance = instance;

        // handling user classes (setting __wrapper field)
        if (R.isUserClass(wrapperClass)) {
            actualClass.inst(instance).fld("__wrapper", wrapperClass).set(this);
        }

        // saving interface instances
        if (this instanceof WrapperInterface thisIface) {
            for (Class<?> iface : wrapperClass.getInterfaces()) {
                if (WrapperInterface.class.isAssignableFrom(iface)) {
                    Class<?> ifaceWrapperClass = (Class<?>) R.clz(iface).fld("wrapper", Class.class).get();
                    Map<WrapperInterface, Wrapper<?>> instances = (Map<WrapperInterface, Wrapper<?>>) R.clz(iface).fld("instances", Map.class).get();
                    instances.put(thisIface, R.wrapperInst((Class<Wrapper<?>>) ifaceWrapperClass, instance));
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

    public boolean equals(S wrapper) {
        if (wrapper == null) {
            return false;
        }
        return Objects.equals(instance, wrapper.instance);
    }

    public interface DelayedConstructor {
        Object construct(R.RClass actualClass);
    }
}

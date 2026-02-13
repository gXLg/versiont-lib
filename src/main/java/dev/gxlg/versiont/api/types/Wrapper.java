package dev.gxlg.versiont.api.types;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;

public abstract class Wrapper<S extends Wrapper<S>> {
    protected final Object instance;

    protected Wrapper(Object instance) {
        if (instance == null) {
            throw new RuntimeException("Cannot wrap null instance");
        }
        this.instance = instance;
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

    public static class Interceptor {
        @RuntimeType
        public static Object intercept(
            @Origin Method method, @FieldValue(
                "__wrapper"
            ) Wrapper<?> wrapper, @AllArguments Object[] args, @SuperCall Callable<?> superCall
        ) throws Exception {
            return superCall.call();
        }
    }
}

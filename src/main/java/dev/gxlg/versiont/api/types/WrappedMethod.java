package dev.gxlg.versiont.api.types;

import java.lang.reflect.Method;
import java.util.function.Function;

public record WrappedMethod(Function<Method, Boolean> matcher, Invoker invoker) {
    public boolean matches(Method method) {
        try {
            return matcher.apply(method);
        } catch (Exception ignored) {
            return false;
        }
    }

    public Object invoke(Object wrapper, Object[] args) throws Exception {
        return invoker.invoke(wrapper, args);
    }

    public interface Invoker {
        Object invoke(Object wrapper, Object[] args) throws Exception;
    }
}

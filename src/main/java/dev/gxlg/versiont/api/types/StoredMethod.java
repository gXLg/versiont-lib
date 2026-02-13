package dev.gxlg.versiont.api.types;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public abstract class StoredMethod {
    public abstract Object invk(Object instance, Object[] args);

    private static final MethodType STATIC_METHOD_TYPE = MethodType.methodType(Object.class, Object[].class);

    private static final MethodType METHOD_TYPE = MethodType.methodType(Object.class, Object.class, Object[].class);

    public static StoredMethod of(int args, boolean isInstance, MethodHandle method) {
        if (isInstance) {
            return new Instance(args, method);
        } else {
            return new Static(args, method);
        }
    }

    public static class Instance extends StoredMethod {
        private final MethodHandle method;

        private Instance(int args, MethodHandle method) {
            this.method = method.asSpreader(Object[].class, args).asType(METHOD_TYPE);
        }

        @Override
        public Object invk(Object instance, Object[] args) {
            try {
                return method.invokeExact(instance, args);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class Static extends StoredMethod {
        private final MethodHandle method;

        private Static(int args, MethodHandle method) {
            this.method = method.asSpreader(Object[].class, args).asType(STATIC_METHOD_TYPE);
        }

        @Override
        public Object invk(Object instance, Object[] args) {
            try {
                return method.invokeExact(args);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}

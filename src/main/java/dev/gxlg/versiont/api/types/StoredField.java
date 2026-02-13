package dev.gxlg.versiont.api.types;

import java.lang.invoke.VarHandle;

public abstract class StoredField {
    public abstract Object get(Object instance);

    public abstract void set(Object instance, Object value);

    public static StoredField of(boolean isInstance, VarHandle handle) {
        if (isInstance) {
            return new Instance(handle);
        } else {
            return new Static(handle);
        }
    }

    public static class Instance extends StoredField {
        private final VarHandle varHandle;

        private Instance(VarHandle handle) {
            this.varHandle = handle;
        }

        @Override
        public Object get(Object instance) {
            return varHandle.get(instance);
        }

        @Override
        public void set(Object instance, Object value) {
            varHandle.set(instance, value);
        }
    }

    public static class Static extends StoredField {
        private final VarHandle varHandle;

        private Static(VarHandle handle) {
            this.varHandle = handle;
        }

        @Override
        public Object get(Object instance) {
            return varHandle.get();
        }

        @Override
        public void set(Object instance, Object value) {
            varHandle.set(value);
        }
    }
}

package dev.gxlg.versiont.api.types;

public interface WrapperInterface {
    Object unwrap();

    default <S> S unwrap(Class<S> clazz) {
        return clazz.cast(unwrap());
    }
}

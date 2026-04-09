package dev.gxlg.versiont.mixins;

import dev.gxlg.versiont.api.V;

import java.util.function.Function;

public enum Comparison {
    HIGHER(V::higher),
    LOWER(V::lower),
    EQUAL(V::equal),
    NOT_HIGHER(v -> !V.higher(v)),
    NOT_LOWER(v -> !V.lower(v)),
    NOT_EQUAL(v -> !V.equal(v));

    private final Function<String, Boolean> comparator;

    Comparison(Function<String, Boolean> comparator) {
        this.comparator = comparator;
    }

    public boolean compare(String version) {
        return comparator.apply(version);
    }
}

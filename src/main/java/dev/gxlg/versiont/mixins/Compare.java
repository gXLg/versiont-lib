package dev.gxlg.versiont.mixins;

@SuppressWarnings("unused")
public @interface Compare {
    String version();

    Comparison comparison();
}

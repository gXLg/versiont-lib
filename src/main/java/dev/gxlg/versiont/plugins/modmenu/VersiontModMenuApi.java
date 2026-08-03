package dev.gxlg.versiont.plugins.modmenu;

import dev.gxlg.versiont.api.types.Wrapper;

public interface VersiontModMenuApi {
    Wrapper<?> getModConfigScreen(Wrapper<?> parentScreen);

    Wrapper<?> wrapScreen(Object screen);
}

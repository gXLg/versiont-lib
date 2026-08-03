package dev.gxlg.versiont.plugins.modmenu;

import dev.gxlg.versiont.api.R;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

@SuppressWarnings("unused")
public class ModMenuAdapter implements LanguageAdapter {
    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        String modId = mod.getMetadata().getId();
        Class<?> apiClass = R.clz("com.terraformersmc.modmenu.api.ModMenuApi").self();
        if (!apiClass.isAssignableFrom(type)) {
            Class<?> markerClass = R.clz("com.terraformersmc.modmenu.util.ModMenuApiMarker").self();
            if (!markerClass.isAssignableFrom(type)) {
                throw new LanguageAdapterException("Mod '" + modId + "' uses the 'versiont+modmenu' adapter, but not for the ModMenu entrypoint! Instead: '" + type.getName() + "'");
            }
        }
        try {
            R.RClass entryClass = R.clz(value);
            if (!VersiontModMenuApi.class.isAssignableFrom(entryClass.self())) {
                throw new LanguageAdapterException("Mod '" + modId + "' uses the 'versiont+modmenu' adapter, but the entrypoint does not implement 'VersiontModMenuApi'!");
            }
            VersiontModMenuApi vApi = (VersiontModMenuApi) entryClass.constr().newInst().self();
            Class<?> factoryClass = R.clz("com.terraformersmc.modmenu.api.ConfigScreenFactory").self();
            Object factory = Proxy.newProxyInstance(
                factoryClass.getClassLoader(), new Class[]{ factoryClass }, (proxy, method, args) -> {
                    if (method.getName().equals("create")) {
                        Object parentScreen = args[0];
                        return R.unwrapWrapper(vApi.getModConfigScreen(vApi.wrapScreen(parentScreen)));
                    }
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
            );
            Object mApi = Proxy.newProxyInstance(
                apiClass.getClassLoader(), new Class[]{ apiClass }, (proxy, method, args) -> {
                    if (method.getName().equals("getModConfigScreenFactory")) {
                        return factory;
                    }
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
            );
            return (T) mApi;

        } catch (Throwable t) {
            throw new LanguageAdapterException(t);
        }
    }
}

package dev.gxlg.versiont.mixins;

import dev.gxlg.versiont.api.V;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public class VersiontMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            ClassNode mixin = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName);

            if (mixin.visibleAnnotations == null) {
                return true;
            }

            for (AnnotationNode annotation : mixin.visibleAnnotations) {
                if (!versiontMixinDesc.equals(annotation.desc)) {
                    continue;
                }

                List<AnnotationNode> compares = Annotations.getValue(annotation, "compare");
                boolean obfuscated = Annotations.getValue(annotation, "obfuscated", (Boolean) false);

                for (AnnotationNode compareAnnotation : compares) {
                    String version = Annotations.getValue(compareAnnotation, "version");
                    Comparison comparison = Annotations.getValue(compareAnnotation, "comparison", Comparison.class, Comparison.EQUAL);
                    if (!comparison.compare(version)) {
                        return false;
                    }
                }
                if (obfuscated != V.isObfuscated()) {
                    return false;
                }
            }

        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static final String versiontMixinDesc = Type.getDescriptor(VersiontMixin.class);
}

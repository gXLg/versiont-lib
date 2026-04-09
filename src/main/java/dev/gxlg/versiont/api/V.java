package dev.gxlg.versiont.api;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public class V {
    private static final AtomicReference<MinecraftVersion> version = new AtomicReference<>();

    private static final AtomicReference<Boolean> obfuscated = new AtomicReference<>();

    public static MinecraftVersion getVersion() {
        return version.updateAndGet(v -> {
            if (v != null) {
                return v;
            }
            FabricLoader loader = FabricLoader.getInstance();
            Optional<ModContainer> modContainer = loader.getModContainer("minecraft");
            if (modContainer.isEmpty()) {
                throw new RuntimeException("Version't failed to determine Minecraft version, please report this to the developer along with your Minecraft version and mod list");
            }
            Version version = modContainer.get().getMetadata().getVersion();
            return new MinecraftVersion(version.getFriendlyString());
        });
    }

    public static boolean isObfuscated() {
        return obfuscated.updateAndGet(obf -> {
            if (obf != null) {
                return obf;
            }
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                return false;
            }
            return lower("26.1");
        });
    }

    public static boolean higher(String other) {
        return getVersion().higher(other);
    }

    public static boolean equal(String other) {
        return getVersion().equal(other);
    }

    public static boolean lower(String other) {
        return getVersion().lower(other);
    }

    public static class MinecraftVersion {
        private final int major;

        private final int minor;

        private final int patch;

        private final Map<String, Integer> cache = new ConcurrentHashMap<>();

        public MinecraftVersion(String version) {
            String[] mainParts = version.split("[^0-9.]", 2);
            String[] nums = mainParts[0].split("\\.");
            this.major = nums.length > 0 ? Integer.parseInt(nums[0]) : 0;
            this.minor = nums.length > 1 ? Integer.parseInt(nums[1]) : 0;
            this.patch = nums.length > 2 ? Integer.parseInt(nums[2]) : 0;
        }

        public int compare(String other) {
            return cache.computeIfAbsent(
                other, i -> {
                    MinecraftVersion v = new MinecraftVersion(other);
                    if (this.major != v.major) {
                        return Integer.compare(this.major, v.major);
                    }
                    if (this.minor != v.minor) {
                        return Integer.compare(this.minor, v.minor);
                    }
                    return Integer.compare(this.patch, v.patch);
                }
            );
        }

        public boolean higher(String other) {
            return this.compare(other) > 0;
        }

        public boolean lower(String other) {
            return this.compare(other) < 0;
        }

        public boolean equal(String other) {
            return this.compare(other) == 0;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}

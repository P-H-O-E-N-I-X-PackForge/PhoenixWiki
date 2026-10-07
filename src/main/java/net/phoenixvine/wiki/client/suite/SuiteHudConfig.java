package net.phoenixvine.wiki.client.suite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.phoenixvine.wiki.PhoenixWiki;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SuiteHudConfig {

    private SuiteHudConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;

    private static final class Data {

        Set<String> disabled = new HashSet<>();
        float globalScale = 1.0f;
        Map<String, Float> buttonScale = new HashMap<>();
        Map<String, int[]> buttonAnchor = new HashMap<>();
        Map<String, Integer> hideCount = new HashMap<>();
        Map<String, Integer> hoverCount = new HashMap<>();
        boolean gravityMode = false;
        Set<String> gravityPlaced = new HashSet<>();
    }

    private static Data data = null;

    public static void init() {
        synchronized (LOCK) {
            ensureLoaded();
        }
    }

    private static File file() {
        return new File(Minecraft.getInstance().gameDirectory, "config/phoenix_wiki/suite_hud.json");
    }

    private static File legacyFile() {
        return new File(Minecraft.getInstance().gameDirectory, "config/phoenix-wiki-suite.json");
    }

    private static void ensureLoaded() {
        if (data != null) return;
        data = new Data();
        var file = file();
        if (!file.exists() && legacyFile().exists()) {
            file = legacyFile();
        }
        if (file.exists()) {
            try (FileReader r = new FileReader(file)) {
                JsonElement root = JsonParser.parseReader(r);
                if (root != null && root.isJsonArray()) {
                    
                    for (JsonElement e : root.getAsJsonArray()) data.disabled.add(e.getAsString());
                } else if (root != null && root.isJsonObject()) {
                    Data loaded = GSON.fromJson(root, Data.class);
                    if (loaded != null) {
                        if (loaded.disabled != null) data.disabled.addAll(loaded.disabled);
                        if (loaded.buttonScale != null) data.buttonScale.putAll(loaded.buttonScale);
                        if (loaded.buttonAnchor != null) data.buttonAnchor.putAll(loaded.buttonAnchor);
                        if (loaded.hideCount != null) data.hideCount.putAll(loaded.hideCount);
                        if (loaded.hoverCount != null) data.hoverCount.putAll(loaded.hoverCount);
                        if (loaded.gravityPlaced != null) data.gravityPlaced.addAll(loaded.gravityPlaced);
                        data.gravityMode = loaded.gravityMode;
                        data.globalScale = clamp(loaded.globalScale <= 0f ? 1.0f : loaded.globalScale);
                    }
                }
            } catch (Exception e) {
                PhoenixWiki.LOGGER.warn("[PhoenixWiki] Failed to load suite config: {}", e.getMessage());
            }
        }

        save();
    }

    private static float clamp(float scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static boolean isEnabled(String modId) {
        synchronized (LOCK) {
            ensureLoaded();
            return !data.disabled.contains(modId);
        }
    }

    public static void setEnabled(String modId, boolean enabled) {
        synchronized (LOCK) {
            ensureLoaded();
            if (enabled) data.disabled.remove(modId);
            else data.disabled.add(modId);
            save();
        }
    }

    public static void toggleEnabled(String modId) {
        setEnabled(modId, !isEnabled(modId));
    }

    public static float getGlobalScale() {
        synchronized (LOCK) {
            ensureLoaded();
            return data.globalScale;
        }
    }

    public static void setGlobalScale(float scale) {
        synchronized (LOCK) {
            ensureLoaded();
            data.globalScale = clamp(scale);
            save();
        }
    }

    public static float getEffectiveScale(String modId) {
        synchronized (LOCK) {
            ensureLoaded();
            Float override = data.buttonScale.get(modId);
            return override != null ? override : data.globalScale;
        }
    }

    public static Float getButtonScaleOverride(String modId) {
        synchronized (LOCK) {
            ensureLoaded();
            return data.buttonScale.get(modId);
        }
    }

    public static void setButtonScale(String modId, Float scale) {
        synchronized (LOCK) {
            ensureLoaded();
            if (scale == null) data.buttonScale.remove(modId);
            else data.buttonScale.put(modId, clamp(scale));
            save();
        }
    }

    @Nullable
    public static int[] getButtonAnchor(String key) {
        synchronized (LOCK) {
            ensureLoaded();
            return data.buttonAnchor.get(key);
        }
    }

    public static void setButtonAnchorLive(String key, boolean anchorRight, int distX, boolean anchorBottom,
                                           int distY) {
        synchronized (LOCK) {
            ensureLoaded();
            data.buttonAnchor.put(key, new int[]{anchorRight ? 1 : 0, distX, anchorBottom ? 1 : 0, distY});
        }
    }

    public static void commitButtonAnchor(String key, int @Nullable [] anchor) {
        synchronized (LOCK) {
            ensureLoaded();
            if (anchor == null) data.buttonAnchor.remove(key);
            else data.buttonAnchor.put(key, anchor);
            save();
        }
    }

    public static void clearAllButtonAnchors() {
        synchronized (LOCK) {
            ensureLoaded();
            data.buttonAnchor.clear();
            save();
        }
    }

    public static int incrementHideCount(String modId) {
        synchronized (LOCK) {
            ensureLoaded();
            int next = data.hideCount.getOrDefault(modId, 0) + 1;
            data.hideCount.put(modId, next);
            save();
            return next;
        }
    }

    public static int incrementHoverCount(String modId) {
        synchronized (LOCK) {
            ensureLoaded();
            int next = data.hoverCount.getOrDefault(modId, 0) + 1;
            data.hoverCount.put(modId, next);
            save();
            return next;
        }
    }

    public static int getHoverCount(String modId) {
        synchronized (LOCK) {
            ensureLoaded();
            return data.hoverCount.getOrDefault(modId, 0);
        }
    }

    public static boolean isGravityMode() {
        synchronized (LOCK) {
            ensureLoaded();
            return data.gravityMode;
        }
    }

    public static void setGravityModeFlag(boolean enabled) {
        synchronized (LOCK) {
            ensureLoaded();
            data.gravityMode = enabled;
            save();
        }
    }

    public static void markGravityPlaced(String key) {
        synchronized (LOCK) {
            ensureLoaded();
            data.gravityPlaced.add(key);
            save();
        }
    }

    public static void clearGravityPiles() {
        synchronized (LOCK) {
            ensureLoaded();
            for (String key : data.gravityPlaced) data.buttonAnchor.remove(key);
            data.gravityPlaced.clear();
            save();
        }
    }

    public static void unmarkGravityPlaced(String key) {
        synchronized (LOCK) {
            ensureLoaded();
            if (data.gravityPlaced.remove(key)) save();
        }
    }

    private static void save() {
        try {
            File f = file();
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            PhoenixWiki.LOGGER.warn("[PhoenixWiki] Failed to save suite config: {}", e.getMessage());
        }
    }
}

package net.phoenixvine.wiki.theme;

import com.google.gson.JsonElement;
import net.minecraft.util.Mth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import net.phoenixvine.wiki.PhoenixWiki;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PhoenixTheme {

    public static class ThemeColor {

        public String hex;
        private transient Integer cached = null;

        private transient long animOffset = 0L;

        public ThemeColor() {
            this.hex = "FFFFFFFF";
        }

        public ThemeColor(String hex) {
            this.hex = hex;
        }

        void setAnimOffset(long offset) {
            this.animOffset = offset;
        }

        public int getColor() {
            if (hex == null) return 0xFFFFFFFF;
            String clean = hex.trim().toUpperCase(Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);

            switch (clean) {
                case "RAINBOW":
                    return animatedHue(6000L, 0.75f, 0.90f, 0f, 1f);
                case "PASTEL_RAINBOW":
                    return animatedHue(12000L, 0.38f, 0.95f, 0f, 1f);
                case "MAGMA":
                    return animatedWave(5000.0, 0.95f, 0.50f, 0.45f, 0f, 0.10f);
                case "AURORA":
                    return animatedWave(3200.0, 0.90f, 0.85f, 0f, 0.32f, 0.18f);
                case "GALAXY":
                    return animatedWave(4000.0, 0.85f, 0.90f, 0f, 0.75f, 0.14f);
            }

            if (cached == null) {
                try {
                    cached = (int) Long.parseUnsignedLong(clean, 16);
                } catch (Exception e) {
                    cached = 0xFFFFFFFF;
                }
            }
            return cached;
        }

        private static long animClock() {
            return PhoenixTheme.isReduceMotion() ? 0L : System.currentTimeMillis();
        }

        private int animatedHue(long periodMs, float sat, float val, float hueMin, float hueMax) {
            float hue = (float) ((animClock() + animOffset) % periodMs) / periodMs;
            return hsvToRgb(hueMin + hue * (hueMax - hueMin), sat, val);
        }

        private int animatedWave(double periodMs, float sat, float valBase, float valAmp, float hueBase,
                                 float hueAmp) {
            double t = (animClock() + animOffset) / periodMs;
            float wave = (float) (Math.sin(t) * 0.5 + 0.5);
            return hsvToRgb(hueBase + wave * hueAmp, sat, valBase + wave * valAmp);
        }

        private static int hsvToRgb(float h, float s, float v) {
            int i = (int) (h * 6);
            float f = h * 6 - i;
            float p = v * (1f - s);
            float q = v * (1f - s * f);
            float t = v * (1f - s * (1f - f));
            float r, g, b;
            switch (((i % 6) + 6) % 6) {
                case 0 -> {
                    r = v;
                    g = t;
                    b = p;
                }
                case 1 -> {
                    r = q;
                    g = v;
                    b = p;
                }
                case 2 -> {
                    r = p;
                    g = v;
                    b = t;
                }
                case 3 -> {
                    r = p;
                    g = q;
                    b = v;
                }
                case 4 -> {
                    r = t;
                    g = p;
                    b = v;
                }
                default -> {
                    r = v;
                    g = p;
                    b = q;
                }
            }
            return (0xFF << 24) | (Mth.clamp((int) (r * 255), 0, 255) << 16) |
                    (Mth.clamp((int) (g * 255), 0, 255) << 8) | Mth.clamp((int) (b * 255), 0, 255);
        }

        public void set(String newHex) {
            this.hex = newHex;
            this.cached = null;
        }
    }

    public ThemeColor bg, panel, header, border, accent, text, textDim, textFaint, done, activeColor, locked, ally;

    public static final Map<String, PhoenixTheme> REGISTRY = new LinkedHashMap<>();
    private static PhoenixTheme active = null;
    private static String activeName = "DARK";

    private static boolean sharedMode = true;

    private static final Map<String, String> perModActiveTheme = new LinkedHashMap<>();

    private static final Map<String, String> MOD_PACKAGES = new LinkedHashMap<>();

    private static final String OWN_PACKAGE = "net.phoenixvine.wiki";

    public static void registerMod(String packagePrefix, String modId) {
        MOD_PACKAGES.put(packagePrefix, modId);
    }

    static String resolveCallerModId() {
        if (MOD_PACKAGES.isEmpty()) return null;
        String callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(f -> f.getClassName())
                        .filter(n -> !n.startsWith(OWN_PACKAGE))
                        .findFirst())
                .orElse(null);
        if (callerClass == null) return null;

        String best = null;
        for (String prefix : MOD_PACKAGES.keySet()) {
            if (callerClass.startsWith(prefix) && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        return best != null ? MOD_PACKAGES.get(best) : null;
    }

    public static boolean isSharedMode() {
        if (REGISTRY.isEmpty()) loadThemes();
        return sharedMode;
    }

    public static void setSharedMode(boolean shared) {
        if (REGISTRY.isEmpty()) loadThemes();
        sharedMode = shared;
        saveAll();
        fireChangeListeners();
    }

    private static final java.util.List<Runnable> CHANGE_LISTENERS = new java.util.ArrayList<>();

    public static void addChangeListener(Runnable listener) {
        CHANGE_LISTENERS.add(listener);
    }

    private static void fireChangeListeners() {
        for (Runnable r : CHANGE_LISTENERS) {
            try {
                r.run();
            } catch (Exception e) {
                PhoenixWiki.LOGGER.error("Suite theme change listener threw", e);
            }
        }
    }

    private static boolean reduceMotion = false;

    public static boolean isReduceMotion() {
        return reduceMotion;
    }

    public static void setReduceMotion(boolean value) {
        reduceMotion = value;
    }

    private static final Set<String> BUILTINS = Set.of("DARK", "LIGHT", "PASTEL", "CRIMSON", "OCEAN", "PHANTOM",
            "EMBER", "RAINBOW", "PASTEL_RAINBOW", "MAGMA");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path THEMES_FILE = FMLPaths.CONFIGDIR.get().resolve("phoenix_wiki").resolve("theme.json");
    private static final Path LEGACY_THEMES_FILE = FMLPaths.CONFIGDIR.get().resolve("phoenixsuite_theme.json");

    public PhoenixTheme() {}

    public PhoenixTheme(String bg, String panel, String header, String border, String accent,
                        String text, String textDim, String textFaint, String done, String activeCol,
                        String locked) {
        this(bg, panel, header, border, accent, text, textDim, textFaint, done, activeCol, locked, accent);
    }

    public PhoenixTheme(String bg, String panel, String header, String border, String accent,
                        String text, String textDim, String textFaint, String done, String activeCol,
                        String locked, String ally) {
        this.bg = new ThemeColor(bg);
        this.panel = new ThemeColor(panel);
        this.header = new ThemeColor(header);
        this.border = new ThemeColor(border);
        this.accent = new ThemeColor(accent);
        this.text = new ThemeColor(text);
        this.textDim = new ThemeColor(textDim);
        this.textFaint = new ThemeColor(textFaint);
        this.done = new ThemeColor(done);
        this.activeColor = new ThemeColor(activeCol);
        this.locked = new ThemeColor(locked);
        this.ally = new ThemeColor(ally);
        assignAnimOffsets();
    }

    private void assignAnimOffsets() {
        if (bg != null) bg.setAnimOffset(0);
        if (panel != null) panel.setAnimOffset(500);
        if (header != null) header.setAnimOffset(1000);
        if (border != null) border.setAnimOffset(1500);
        if (accent != null) accent.setAnimOffset(2000);
        if (text != null) text.setAnimOffset(2500);
        if (textDim != null) textDim.setAnimOffset(3000);
        if (textFaint != null) textFaint.setAnimOffset(3500);
        if (done != null) done.setAnimOffset(4000);
        if (activeColor != null) activeColor.setAnimOffset(4500);
        if (locked != null) locked.setAnimOffset(5000);
        if (ally != null) ally.setAnimOffset(5500);
    }

    public PhoenixTheme copy() {
        return new PhoenixTheme(
                bg.hex, panel.hex, header.hex, border.hex, accent.hex,
                text.hex, textDim.hex, textFaint.hex, done.hex, activeColor.hex, locked.hex, ally.hex);
    }

    public static PhoenixTheme current() {
        return current(resolveCallerModId());
    }

    public static PhoenixTheme current(String modId) {
        if (REGISTRY.isEmpty()) loadThemes();
        PhoenixTheme fallback = active != null ? active : REGISTRY.get("DARK");
        if (sharedMode || modId == null) return fallback;
        String name = perModActiveTheme.get(modId);
        if (name == null) return fallback;
        PhoenixTheme t = REGISTRY.get(name);
        return t != null ? t : fallback;
    }

    public static String getActiveName() {
        return getActiveName(resolveCallerModId());
    }

    public static String getActiveName(String modId) {
        if (REGISTRY.isEmpty()) loadThemes();
        if (sharedMode || modId == null) return activeName;
        return perModActiveTheme.getOrDefault(modId, activeName);
    }

    public static boolean isBuiltin(String name) {
        return BUILTINS.contains(name.toUpperCase(Locale.ROOT));
    }

    public static void setCurrent(String name) {
        setCurrent(resolveCallerModId(), name);
    }

    public static void setCurrent(String modId, String name) {
        if (REGISTRY.isEmpty()) loadThemes();
        PhoenixTheme t = REGISTRY.get(name);
        if (t == null) t = REGISTRY.get(name.toUpperCase(Locale.ROOT));
        if (t == null) return;

        if (sharedMode || modId == null) {
            active = t;
            activeName = name;
        } else {
            perModActiveTheme.put(modId, name);
        }
        saveAll();
        fireChangeListeners();
    }

    public static void saveCustomTheme(String name, PhoenixTheme theme) {
        if (isBuiltin(name)) return;
        REGISTRY.put(name, theme);
        saveAll();
        fireChangeListeners();
    }

    public static String createNewTheme(String baseName, PhoenixTheme source) {
        return createNewTheme(resolveCallerModId(), baseName, source);
    }

    public static String createNewTheme(String modId, String baseName, PhoenixTheme source) {
        if (REGISTRY.isEmpty()) loadThemes();
        String candidate = baseName;
        int n = 2;
        while (REGISTRY.containsKey(candidate) || isBuiltin(candidate)) {
            candidate = baseName + " " + n;
            n++;
        }
        saveCustomTheme(candidate, source.copy());
        setCurrent(modId, candidate);
        return candidate;
    }

    public static void deleteCustom(String name) {
        if (isBuiltin(name)) return;
        if (REGISTRY.remove(name) == null) return;
        if (name.equals(activeName)) {
            activeName = "DARK";
            active = REGISTRY.get("DARK");
        }

        perModActiveTheme.values().removeIf(name::equals);
        saveAll();
        fireChangeListeners();
    }

    private static JsonObject themeToJson(PhoenixTheme t) {
        JsonObject o = new JsonObject();
        o.addProperty("bg", t.bg.hex);
        o.addProperty("panel", t.panel.hex);
        o.addProperty("header", t.header.hex);
        o.addProperty("border", t.border.hex);
        o.addProperty("accent", t.accent.hex);
        o.addProperty("text", t.text.hex);
        o.addProperty("textDim", t.textDim.hex);
        o.addProperty("textFaint", t.textFaint.hex);
        o.addProperty("done", t.done.hex);
        o.addProperty("activeColor", t.activeColor.hex);
        o.addProperty("locked", t.locked.hex);
        o.addProperty("ally", t.ally.hex);
        return o;
    }

    private static PhoenixTheme themeFromJson(JsonObject o) {
        String accent = field(o, "accent");
        return new PhoenixTheme(
                field(o, "bg"), field(o, "panel"), field(o, "header"), field(o, "border"), accent,
                field(o, "text"), field(o, "textDim"), field(o, "textFaint"), field(o, "done"),
                field(o, "activeColor"), field(o, "locked"),

                o.has("ally") ? field(o, "ally") : accent);
    }

    private static String field(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "FFFFFFFF";
    }

    public static void saveAll() {
        try {
            Files.createDirectories(THEMES_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("active", activeName);
            root.addProperty("sharedMode", sharedMode);
            JsonObject perModObj = new JsonObject();
            for (Map.Entry<String, String> e : perModActiveTheme.entrySet()) perModObj.addProperty(e.getKey(), e.getValue());
            root.add("perMod", perModObj);
            JsonObject customObj = new JsonObject();
            for (Map.Entry<String, PhoenixTheme> e : REGISTRY.entrySet()) {
                if (!isBuiltin(e.getKey())) customObj.add(e.getKey(), themeToJson(e.getValue()));
            }
            root.add("custom", customObj);
            Files.writeString(THEMES_FILE, GSON.toJson(root));
        } catch (Exception e) {
           PhoenixWiki.LOGGER.error("Failed to save {}", THEMES_FILE, e);
        }
    }

    public static void loadThemes() {
        REGISTRY.clear();

        REGISTRY.put("DARK", new PhoenixTheme(
                "FF0B0B0F", "FF14141A", "FF0C0C10", "FF353548", "FF00AA55",
                "FFD8D8E4", "FF7A7A8A", "FF404050", "FF44CC88", "FFFFBB33", "FF606070", "FF4499FF"));

        REGISTRY.put("LIGHT", new PhoenixTheme(
                "FFF0F0F4", "FFE4E4EC", "FFD8D8E0", "FFA0A0B0", "FF0088CC",
                "FF1A1A2A", "FF555565", "FF888898", "FF22AA55", "FFCC6600", "FF808090", "FF3377CC"));

        REGISTRY.put("PASTEL", new PhoenixTheme(
                "FFFCEEF6", "FFF6E0EF", "FFF0D4E8", "FFE0B8D4", "FFCC66AA",
                "FF4A3550", "FF8A7090", "FFB8A0C0", "FF66BB88", "FFEEAA55", "FFB0A0B8", "FF77AADD"));

        REGISTRY.put("CRIMSON", new PhoenixTheme(
                "FF0F0808", "FF1A0C0C", "FF0D0606", "FF3A1818", "FFCC2233",
                "FFE4D0D0", "FF8A6A6A", "FF503838", "FF44CC66", "FFFFAA33", "FF705555", "FF4488CC"));

        REGISTRY.put("OCEAN", new PhoenixTheme(
                "FF080C12", "FF0E1520", "FF080C14", "FF1E2A3C", "FF0099CC",
                "FFCCE0F0", "FF6080A0", "FF304060", "FF33CC88", "FFFFBB33", "FF506888", "FF33AAEE"));

        REGISTRY.put("PHANTOM", new PhoenixTheme(
                "FF0A080F", "FF130E1C", "FF0A0810", "FF2E2040", "FF8833CC",
                "FFD8CCF0", "FF7060A0", "FF3C2C5C", "FF44BB88", "FFFFAA44", "FF604880", "FF7799EE"));

        REGISTRY.put("EMBER", new PhoenixTheme(
                "FF100A06", "FF1C120A", "FF100A04", "FF382210", "FFCC6600",
                "FFF0E0CC", "FFA0785A", "FF604830", "FF44CC77", "FFFFCC22", "FF806040", "FF4499CC"));

        REGISTRY.put("RAINBOW", new PhoenixTheme(
                "FF09090C", "FF111116", "FF0A0A0E", "RAINBOW", "RAINBOW",
                "FFEEEEEE", "FF888888", "FF505050", "FF44CC88", "RAINBOW", "FF606070", "FF4499FF"));

        REGISTRY.put("PASTEL_RAINBOW", new PhoenixTheme(
                "FFFAF0F8", "FFF6E8F2", "FFF2DCEC", "PASTEL_RAINBOW", "PASTEL_RAINBOW",
                "FF4A3550", "FF8A7090", "FFB8A0C0", "FF66BB88", "PASTEL_RAINBOW", "FFB0A0B8", "FF77AADD"));

        REGISTRY.put("MAGMA", new PhoenixTheme(
                "FF120806", "FF1C0E0A", "FF100604", "MAGMA", "MAGMA",
                "FFF0E0D0", "FFA07860", "FF604838", "FF44CC77", "MAGMA", "FF705040", "FF4499CC"));

        String loadedActive = "DARK";
        sharedMode = true;
        perModActiveTheme.clear();
        try {
            Path sourceFile = Files.exists(THEMES_FILE) ? THEMES_FILE :
                    Files.exists(LEGACY_THEMES_FILE) ? LEGACY_THEMES_FILE : null;
            if (sourceFile != null) {
                String json = Files.readString(sourceFile);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root != null) {
                    if (root.has("custom") && root.get("custom").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("custom")
                                .entrySet()) {
                            if (!e.getValue().isJsonObject()) continue;
                            REGISTRY.put(e.getKey(), themeFromJson(e.getValue().getAsJsonObject()));
                        }
                    }
                    if (root.has("active")) loadedActive = root.get("active").getAsString();
                    if (root.has("sharedMode")) sharedMode = root.get("sharedMode").getAsBoolean();
                    if (root.has("perMod") && root.get("perMod").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("perMod")
                                .entrySet()) {
                            perModActiveTheme.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            PhoenixWiki.LOGGER.error("Failed to load {}", THEMES_FILE, e);
        }

        activeName = loadedActive;
        active = REGISTRY.getOrDefault(activeName, REGISTRY.get("DARK"));

        saveAll();
    }
}

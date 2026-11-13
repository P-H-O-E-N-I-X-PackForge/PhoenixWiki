package net.phoenixvine.wiki.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class UiProfiler {

    public static final int TOGGLE_KEY = InputConstants.KEY_F9;

    private final String name;
    private boolean enabled;
    private final Map<String, double[]> sections = new LinkedHashMap<>();
    private final Map<String, double[]> counters = new LinkedHashMap<>();
    private double fps;
    private float sinceLog;

    public UiProfiler(String name) {
        this.name = name;
    }

    public boolean toggle() {
        enabled = !enabled;
        sections.clear();
        counters.clear();
        return enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public void end(String section, long startedAt) {
        if (!enabled || startedAt == 0L) return;
        average(sections, section, (System.nanoTime() - startedAt) / 1_000_000.0);
    }

    public void count(String key, int amount) {
        if (!enabled) return;
        double[] c = counters.computeIfAbsent(key, k -> new double[] { 0, 0 });

        c[0] += amount;
    }

    public void frame(float dtSeconds) {
        if (!enabled) return;
        for (double[] c : counters.values()) {
            c[1] = c[1] == 0 ? c[0] : c[1] * 0.9 + c[0] * 0.1;
            c[0] = 0;
        }
        average(sections, "(frame)", dtSeconds * 1000.0);
        double current = dtSeconds <= 0 ? 0 : 1.0 / dtSeconds;
        fps = fps == 0 ? current : fps * 0.9 + current * 0.1;
        sinceLog += dtSeconds;
        if (sinceLog >= 5f) {
            sinceLog = 0f;
            net.phoenixvine.wiki.PhoenixWiki.LOGGER.info("[ui profile: {}] {}", name, summary());
        }
    }

    private static void average(Map<String, double[]> map, String key, double ms) {
        double[] v = map.computeIfAbsent(key, k -> new double[] { ms });
        v[0] = v[0] * 0.9 + ms * 0.1;
    }

    private String summary() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%.0f fps", fps));
        sections.forEach((k, v) -> sb.append(String.format(Locale.ROOT, " | %s %.2fms", k, v[0])));
        counters.forEach((k, v) -> sb.append(String.format(Locale.ROOT, " | %s %.0f", k, v[1])));
        return sb.toString();
    }

    public void draw(GuiGraphics g, Font font) {
        if (!enabled) return;
        java.util.List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%s profile (F9)   %.0f fps", name, fps));
        sections.forEach((k, v) -> lines.add(String.format(Locale.ROOT, "  %-10s %6.2f ms", k, v[0])));
        counters.forEach((k, v) -> lines.add(String.format(Locale.ROOT, "  %-10s %6.0f", k, v[1])));
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        g.fill(2, 2, w + 10, 6 + lines.size() * 10, 0xCC000000);
        int y = 5;
        for (String l : lines) {
            g.drawString(font, l, 6, y, 0xFFE6E6A0, false);
            y += 10;
        }
    }
}

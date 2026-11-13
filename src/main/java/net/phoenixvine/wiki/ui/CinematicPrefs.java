package net.phoenixvine.wiki.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.loading.FMLPaths;
import net.phoenixvine.wiki.PhoenixWiki;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether the suite's opening cinematics play every time a screen opens. The choice is kept in
 * {@code config/phoenix_wiki_cinematics.json}, and every screen that has a cinematic shows the same small toggle.
 */
public final class CinematicPrefs {

    private static final Gson GSON = new Gson();
    private static boolean loaded;
    private static boolean playIntro = true;

    private static int bx;
    private static int by;
    private static int bw;
    private static int bh;

    private CinematicPrefs() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("phoenix_wiki_cinematics.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) return;
            JsonObject json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (json != null && json.has("playIntro")) playIntro = json.get("playIntro").getAsBoolean();
        } catch (IOException | RuntimeException e) {
            PhoenixWiki.LOGGER.warn("Could not read the cinematic settings: {}", e.getMessage());
        }
    }

    private static void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("playIntro", playIntro);
            Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PhoenixWiki.LOGGER.warn("Could not save the cinematic settings: {}", e.getMessage());
        }
    }

    /** True if opening cinematics should play. */
    public static boolean playIntro() {
        load();
        return playIntro;
    }

    public static void toggle() {
        load();
        playIntro = !playIntro;
        save();
    }

    /**
     * Draw the small toggle at the top right, under {@code top}. Call it every frame and send clicks to
     * {@link #click}.
     */
    public static void draw(GuiGraphics g, Font font, int screenWidth, int top, int mouseX, int mouseY, float alpha) {
        if (alpha <= 0.01f) return;
        String label = "Intro: " + (playIntro() ? "ON" : "OFF");
        bw = font.width(label) + 12;
        bh = 13;
        bx = screenWidth - bw - 8;
        by = top + 6;
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int accent = playIntro ? 0x6FD08C : 0x8A8A98;
        g.fill(bx, by, bx + bw, by + bh, GlDraw.argb(0x000000, 0.55f * alpha));
        g.fill(bx, by, bx + bw, by + 1, GlDraw.argb(accent, (hover ? 0.9f : 0.5f) * alpha));
        g.fill(bx, by + bh - 1, bx + bw, by + bh, GlDraw.argb(accent, (hover ? 0.9f : 0.5f) * alpha));
        g.fill(bx, by, bx + 1, by + bh, GlDraw.argb(accent, (hover ? 0.9f : 0.5f) * alpha));
        g.fill(bx + bw - 1, by, bx + bw, by + bh, GlDraw.argb(accent, (hover ? 0.9f : 0.5f) * alpha));
        g.drawString(font, label, bx + 6, by + 3, GlDraw.argb(hover ? 0xFFFFFF : accent, alpha), false);
    }

    /** True, after toggling, if the click landed on the toggle. */
    public static boolean click(double mouseX, double mouseY) {
        if (bw == 0 || mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + bh) return false;
        toggle();
        return true;
    }

    /** Forget where the toggle was, so a screen that does not draw it cannot be clicked through. */
    public static void hide() {
        bw = 0;
    }
}

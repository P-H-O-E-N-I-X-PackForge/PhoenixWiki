package net.phoenixvine.wiki.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Title cards and typed lines for the suite's opening cinematics. Everything is sized from the screen, so the text
 * shrinks to fit a small window instead of running off it.
 */
public final class Cinematic {

    private Cinematic() {}

    /**
     * A size factor for the screen: 1 at 480 by 270 GUI pixels (a 1080p window at GUI scale 4, what the screens were drawn
     * for), smaller on smaller screens and a little larger on big ones.
     */
    public static float scaleFor(int width, int height) {
        return Mth.clamp(Math.min(width / 480f, height / 270f), 0.55f, 1.5f);
    }

    /** The biggest scale up to {@code desired} at which the text still fits in {@code maxWidth}. */
    public static float fit(Font font, String text, float desired, float maxWidth) {
        int w = Math.max(1, font.width(text));
        return Math.max(0.5f, Math.min(desired, maxWidth / w));
    }

    /** One centred line with a colour-split glow, scaled to fit. */
    public static void line(GuiGraphics g, Font font, String text, float cx, float y, float scale, int color,
                            float alpha, int glowA, int glowB, float maxWidth) {
        if (text.isEmpty() || alpha <= 0f) return;
        float s = fit(font, text, scale, maxWidth);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(s, s, 1f);
        int x = -font.width(text) / 2;
        g.drawString(font, text, x + 2, 2, GlDraw.argb(0x000000, alpha * 0.9f), false);
        g.drawString(font, text, x - 1, 0, GlDraw.argb(glowA, alpha * 0.32f), false);
        g.drawString(font, text, x + 1, 0, GlDraw.argb(glowB, alpha * 0.32f), false);
        g.drawString(font, text, x, 0, GlDraw.argb(color, alpha), false);
        g.pose().popPose();
    }

    /**
     * The opening title card: the title tracks in from wide letter spacing while a band of light crosses it, a rule
     * draws out beneath, and the subtitle comes up under that. {@code t} is seconds since the card began.
     */
    public static void titleCard(GuiGraphics g, Font font, int width, int height, float t, float length, String title,
                                 String sub, int accent, int accent2) {
        // the card is composed on a 480 by 270 stage and the whole stage is scaled to the screen, so a small window or
        // a high GUI scale shrinks everything together instead of leaving the text too big
        float k = Mth.clamp(Math.min(width / 480f, height / 270f), 0.3f, 2.4f);
        float stageW = width / k;
        float stageH = height / k;
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        stage(g, font, Math.round(stageW), Math.round(stageH), t, length, title, sub, accent, accent2);
        g.pose().popPose();
    }

    private static void stage(GuiGraphics g, Font font, int width, int height, float t, float length, String title,
                              String sub, int accent, int accent2) {
        float in = Mth.clamp(t / 0.6f, 0f, 1f);
        float out = Mth.clamp((length - t) / 0.8f, 0f, 1f);
        float alpha = in * out;
        if (alpha <= 0f) return;
        float ui = scaleFor(width, height);
        float cx = width / 2f;
        float cy = height * 0.42f - 5f * GlDraw.ease(Mth.clamp(t / Math.max(1f, length), 0f, 1f));

        // dust drifting up through the light
        for (int i = 0; i < 34; i++) {
            float px = (GlDraw.noise(i, 3f)) * width;
            float speed = 4f + 10f * GlDraw.noise(i, 5f);
            float py = height * 1.05f - ((t * speed + GlDraw.noise(i, 7f) * height) % (height * 1.1f));
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.3f + i * 1.7f);
            float a = alpha * 0.4f * tw * GlDraw.noise(i, 9f);
            g.fill((int) px, (int) py, (int) px + 1 + (i % 5 == 0 ? 1 : 0), (int) py + 1 + (i % 5 == 0 ? 1 : 0),
                    GlDraw.argb(i % 3 == 0 ? accent2 : 0xFFFFFF, a));
        }
        // an anamorphic streak behind the title: a thin wide band of light that opens from the middle
        float streak = GlDraw.ease(Mth.clamp((t - 0.2f) / 1.4f, 0f, 1f));
        int half = (int) (width * 0.46f * streak);
        int sy = (int) (cy + font.lineHeight * 1.5f);
        for (int row = -3; row <= 3; row++) {
            float f = 1f - Math.abs(row) / 3.6f;
            g.fill((int) cx - half, sy + row, (int) cx + half, sy + row + 1,
                    GlDraw.argb(accent, 0.1f * f * alpha * (0.6f + 0.4f * (float) Math.sin(t * 2f))));
        }

        // the title, letter by letter, spacing closing up
        float tracking = (1f - GlDraw.ease(Mth.clamp(t / 2.2f, 0f, 1f))) * 0.7f;
        float scale = fit(font, title, 3.2f * ui, width * 0.62f);
        float natural = 0f;
        for (int i = 0; i < title.length(); i++) natural += font.width(String.valueOf(title.charAt(i)));
        float spacing = tracking * 9f;
        float total = (natural + spacing * Math.max(0, title.length() - 1)) * scale;
        float fitted = total > width * 0.9f ? width * 0.9f / total : 1f;
        scale *= fitted;
        total *= fitted;
        float x = cx - total / 2f;
        g.pose().pushPose();
        g.pose().translate(0, cy, 0);
        g.pose().scale(1f, 1f, 1f);
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float letterIn = Mth.clamp((t - i * 0.045f) / 0.5f, 0f, 1f);
            if (letterIn <= 0f) {
                x += (font.width(ch) + spacing) * scale;
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(x, 0, 0);
            g.pose().scale(scale, scale, 1f);
            float a = alpha * letterIn;
            g.drawString(font, ch, 2, 2, GlDraw.argb(0x000000, a * 0.9f), false);
            g.drawString(font, ch, -1, 0, GlDraw.argb(accent, a * 0.35f), false);
            g.drawString(font, ch, 1, 0, GlDraw.argb(accent2, a * 0.35f), false);
            g.drawString(font, ch, 0, 0, GlDraw.argb(0xFFFFFF, a), false);
            g.pose().popPose();
            x += (font.width(ch) + spacing) * scale;
        }
        g.pose().popPose();

        // a band of light crossing the title once
        float sweep = (t - 0.5f) / 1.4f;
        if (sweep > 0f && sweep < 1f) {
            float bandX = cx - total / 2f - 40f + (total + 80f) * sweep;
            int h = (int) (font.lineHeight * scale);
            for (int i = 0; i < 9; i++) {
                float f = 1f - Math.abs(i - 4) / 4.5f;
                g.fill((int) bandX + i * 4, (int) cy - 2, (int) bandX + i * 4 + 4, (int) cy + h + 2,
                        GlDraw.argb(0xFFFFFF, 0.12f * f * alpha * (1f - sweep * 0.4f)));
            }
        }

        // the rule
        float rule = GlDraw.ease(Mth.clamp((t - 0.9f) / 1.1f, 0f, 1f));
        int ruleHalf = (int) (Math.min(total, width * 0.5f) / 2f * rule);
        int ruleY = (int) (cy + font.lineHeight * scale + 10 * ui);
        if (ruleHalf > 0) {
            g.fill((int) cx - ruleHalf, ruleY, (int) cx + ruleHalf, ruleY + 1, GlDraw.argb(accent, 0.8f * alpha));
            g.fill((int) cx - ruleHalf / 3, ruleY + 1, (int) cx + ruleHalf / 3, ruleY + 2,
                    GlDraw.argb(accent2, 0.4f * alpha));
        }

        // the subtitle, wrapped to the screen
        float subIn = Mth.clamp((t - 1.5f) / 0.8f, 0f, 1f) * out;
        if (subIn > 0f && sub != null && !sub.isEmpty()) {
            float s = Math.max(0.8f, ui);
            int wrapWidth = (int) (width * 0.7f / s);
            List<FormattedCharSequence> lines = font.split(Component.literal(sub), wrapWidth);
            float y = ruleY + 12 * ui;
            for (FormattedCharSequence seq : lines) {
                g.pose().pushPose();
                g.pose().translate(cx, y, 0);
                g.pose().scale(s, s, 1f);
                int w = font.width(seq);
                g.drawString(font, seq, -w / 2 + 1, 1, GlDraw.argb(0x000000, subIn * 0.8f), false);
                g.drawString(font, seq, -w / 2, 0, GlDraw.argb(0xD8CCBB, subIn), false);
                g.pose().popPose();
                y += (font.lineHeight + 2) * s;
            }
        }
    }

    /** A soft dark frame around the screen, to draw the eye in. {@code strength} is 0 to 1. */
    public static void vignette(GuiGraphics g, int width, int height, float strength) {
        if (strength <= 0f) return;
        int edgeY = (int) (height * 0.28f);
        int edgeX = (int) (width * 0.2f);
        int dark = GlDraw.argb(0x000000, 0.7f * strength);
        int clear = 0x00000000;
        g.fillGradient(0, 0, width, edgeY, dark, clear);
        g.fillGradient(0, height - edgeY, width, height, clear, dark);
        g.fillGradient(0, 0, edgeX, height, dark, clear);
        g.fillGradient(width - edgeX, 0, width, height, clear, dark);
    }

    /** The cinematic push-in: a slow zoom from 1.07 down to 1 over the length of the opening. */
    public static float pushIn(float t, float length) {
        return 1f + 0.07f * (1f - GlDraw.ease(Mth.clamp(t / Math.max(0.1f, length), 0f, 1f)));
    }

    /** How far the letterbox bars reach, as a fraction of the height: wide in the opening, settling to the normal bar. */
    public static float letterbox(float t, float length, float normal) {
        float open = GlDraw.ease(Mth.clamp(t / 1.2f, 0f, 1f));
        float settle = GlDraw.ease(Mth.clamp((t - (length - 1.6f)) / 1.4f, 0f, 1f));
        float wide = 0.13f;
        return normal + (wide - normal) * open * (1f - settle);
    }
}

package net.phoenixvine.wiki.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface RichSpan {

    record Typewriter(float charsPerSecond, boolean onClick, TypewriterGroup group, int offset) {}

    final class TypewriterGroup {

        private final String id;
        private int total;

        public TypewriterGroup(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public int total() {
            return total;
        }

        public void add(int characters) {
            total += characters;
        }
    }

    record TypewriterReveal(String key) implements RichSpan {}

    record Text(String text, Style style, int background, String copyText, float scale,
                Typewriter typewriter) implements RichSpan {
        public Text(String text, Style style, int background, String copyText, float scale) {
            this(text, style, background, copyText, scale, null);
        }

        public Text(String text, Style style) {
            this(text, style, 0, null, 1f);
        }

        public Text(String text, Style style, int background) {
            this(text, style, background, null, 1f);
        }

        public Text(String text, Style style, int background, String copyText) {
            this(text, style, background, copyText, 1f);
        }
    }

    record Link(String label, Style style, String url) implements RichSpan {}

    record Tip(String label, Style style, String tooltip) implements RichSpan {}

    record TipCandidate(String conditionExpr, String tooltip) {}

    record ConditionalTip(String label, Style style, List<TipCandidate> candidates) implements RichSpan {}

    record Image(ResourceLocation texture, int w, int h, float rotation) implements RichSpan {
        public Image(ResourceLocation texture, int w, int h) {
            this(texture, w, h, 0f);
        }

        public int boundsW() {
            if (rotation == 0f) return w;
            double rad = Math.toRadians(rotation);
            return (int) Math.ceil(Math.abs(w * Math.cos(rad)) + Math.abs(h * Math.sin(rad)));
        }

        public int boundsH() {
            if (rotation == 0f) return h;
            double rad = Math.toRadians(rotation);
            return (int) Math.ceil(Math.abs(w * Math.sin(rad)) + Math.abs(h * Math.cos(rad)));
        }
    }

    record ItemIcon(ResourceLocation itemId, String tooltip) implements RichSpan {
        public ItemIcon(ResourceLocation itemId) {
            this(itemId, null);
        }
    }

    record CodeCopy(String code) implements RichSpan {}

    record DetailsToggle(String key) implements RichSpan {}

    record TocJump(int targetY) implements RichSpan {}

    record ChecklistToggle(String key, boolean checkedDefault) implements RichSpan {}

    record Region(int x1, int y1, int x2, int y2, RichSpan span) {

        public boolean contains(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }
}

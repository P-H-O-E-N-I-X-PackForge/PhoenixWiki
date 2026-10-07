package net.phoenixvine.wiki.client.rich.markdown.inline.handlers;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.phoenixvine.wiki.client.rich.markdown.inline.InlineHandler;
import net.phoenixvine.wiki.client.rich.markdown.inline.InlineParseState;

public final class ColorTokenHandler implements InlineHandler {

    private static final float DEFAULT_TYPEWRITER_SPEED = 30f;

    @Override
    public char trigger() {
        return '{';
    }

    @Override
    public int tryHandle(InlineParseState s, int i) {
        int end = s.input.indexOf('}', i + 1);
        if (end <= i) return -1;
        String token = s.input.substring(i + 1, end);

        if (token.startsWith("#") && token.length() == 7 && isHex6(token)) {
            s.flush();
            s.style = s.style.withColor(TextColor.fromRgb((int) Long.parseLong(token.substring(1), 16)));
            return end + 1;
        }
        String lower = token.toLowerCase();
        if (lower.equals("/type") || lower.equals("/typewriter")) {
            s.endTypewriter();
            return end + 1;
        }
        if (lower.equals("type") || lower.equals("typewriter") || lower.startsWith("type:") ||
                lower.startsWith("typewriter:")) {
            float charsPerSecond = DEFAULT_TYPEWRITER_SPEED;
            boolean onClick = false;
            String[] parts = lower.split(":");
            for (int p = 1; p < parts.length; p++) {
                if (parts[p].equals("click")) {
                    onClick = true;
                } else if (!parts[p].equals("auto")) {
                    try {
                        float parsed = Float.parseFloat(parts[p]);
                        if (parsed > 0f) charsPerSecond = parsed;
                    } catch (NumberFormatException ignored) {}
                }
            }
            s.beginTypewriter(charsPerSecond, onClick, i);
            return end + 1;
        }
        if (token.equalsIgnoreCase("reset")) {
            s.flush();
            s.style = Style.EMPTY;
            s.scale = 1f;
            return end + 1;
        }
        if (token.toLowerCase().startsWith("scale:")) {
            try {
                float parsed = Float.parseFloat(token.substring(6));
                s.flush();
                s.scale = parsed;
                return end + 1;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private static boolean isHex6(String token) {
        for (int i = 1; i < 7; i++) {
            char c = token.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
}

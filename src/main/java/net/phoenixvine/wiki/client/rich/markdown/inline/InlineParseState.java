package net.phoenixvine.wiki.client.rich.markdown.inline;

import net.minecraft.network.chat.Style;
import net.phoenixvine.wiki.client.rich.RichSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InlineParseState {

    public final String input;
    public final Map<String, List<RichSpan.TipCandidate>> footnotes;
    public final List<RichSpan> out = new ArrayList<>();
    public final StringBuilder buf = new StringBuilder();

    public Style style = Style.EMPTY;
    public int background = 0;
    public float scale = 1f;

    public float typewriterSpeed = 0f;
    public boolean typewriterOnClick = false;
    public RichSpan.TypewriterGroup typewriterGroup = null;
    private int typewriterOffset = 0;

    public void beginTypewriter(float charsPerSecond, boolean onClick, int position) {
        flush();
        typewriterSpeed = charsPerSecond;
        typewriterOnClick = onClick;
        typewriterGroup = new RichSpan.TypewriterGroup(input.hashCode() + "@" + position);
        typewriterOffset = 0;
    }

    public void endTypewriter() {
        flush();
        typewriterGroup = null;
    }

    public InlineParseState(String input, Map<String, List<RichSpan.TipCandidate>> footnotes) {
        this.input = input;
        this.footnotes = footnotes;
    }

    public int length() {
        return input.length();
    }

    public char charAt(int i) {
        return input.charAt(i);
    }

    public void flush() {
        if (buf.isEmpty()) return;
        String text = buf.toString();
        RichSpan.Typewriter typewriter = null;
        if (typewriterGroup != null) {
            int visible = net.phoenixvine.wiki.client.rich.render.TypewriterSupport.visibleLength(text);
            typewriter = new RichSpan.Typewriter(typewriterSpeed, typewriterOnClick, typewriterGroup,
                    typewriterOffset);
            typewriterOffset += visible;
            typewriterGroup.add(visible);
        }
        out.add(new RichSpan.Text(text, style, background, null, scale, typewriter));
        buf.setLength(0);
    }
}

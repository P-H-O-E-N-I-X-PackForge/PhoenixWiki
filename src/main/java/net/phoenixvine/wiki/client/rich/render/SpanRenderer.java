package net.phoenixvine.wiki.client.rich.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.wiki.client.rich.RichSpan;
import net.phoenixvine.wiki.client.rich.WikiRichTextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpanRenderer {

    private SpanRenderer() {}

    public static final int LINE_H = 10;

    public static final int LINK_COLOR = 0xFF55AAFF;
    public static final int TIP_COLOR = 0xFFAAFFAA;
    public static final int HEADING_COLOR = 0xFFF0F0FF;
    public static final int SUBHEADING_COLOR = 0xFFC0C0CC;
    public static final int H3_COLOR = 0xFFA89CC8;
    public static final int DEFAULT_ACCENT = 0xFF9966FF;

    public static final int GAP_PARAGRAPH = 4;
    public static final int GAP_LIST_ITEM = 2;
    public static final int GAP_HEADING_BEFORE = 8;
    public static final int GAP_HEADING_AFTER = 2;
    public static final int GAP_BLANK = 4;

    public static final float H1_SCALE = 1.35f;
    public static final float H2_SCALE = 1.2f;
    public static final float H3_SCALE = 1.08f;
    public static final float BOLD_SCALE = 1.15f;
    public static final float DEFAULT_SCALE = 1.05f;

    public static float headingScale(int level) {
        return switch (Math.min(Math.max(level, 1), 3)) {
            case 1 -> H1_SCALE;
            case 2 -> H2_SCALE;
            default -> H3_SCALE;
        };
    }

    public static float boldScale(float baseScale, boolean bold) {
        return bold ? baseScale * BOLD_SCALE : baseScale;
    }

    public static String plainText(List<RichSpan> spans) {
        var sb = new StringBuilder();
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) sb.append(t.text());
            else if (s instanceof RichSpan.Link l) sb.append(l.label());
            else if (s instanceof RichSpan.Tip t) sb.append(t.label());
            else if (s instanceof RichSpan.ConditionalTip t) sb.append(t.label());
        }
        return sb.toString();
    }

    public static List<RichSpan> withHeadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        TextColor.fromRgb(HEADING_COLOR & 0xFFFFFF)), t.background(),
                        t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    public static List<RichSpan> withSubheadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        TextColor.fromRgb(SUBHEADING_COLOR & 0xFFFFFF)), t.background(),
                        t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    public static List<RichSpan> withH3Style(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(false).withColor(
                        TextColor.fromRgb(H3_COLOR & 0xFFFFFF)), t.background(),
                        t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    public static List<RichSpan> withStrikethroughStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withStrikethrough(true)
                        .withColor(TextColor.fromRgb(0xFF888888)), t.background(),
                        t.copyText(), t.scale()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    public static void renderSpanList(GuiGraphics g, Font font, List<RichSpan> spans,
                                      int x, int[] curY, int originX, int maxW,
                                      int clipTop, int clipBot, List<RichSpan.Region> regions, float scale) {
        int lineH = Math.round(LINE_H * scale);
        int curX = x;
        for (RichSpan span : spans) {
            if (span instanceof RichSpan.Image img) {
                if (curX > originX) {
                    curX = originX;
                    curY[0] += lineH;
                }
                if (curY[0] >= clipTop && curY[0] + img.h() <= clipBot) {
                    g.blit(WikiRichTextRenderer.imageResolver.apply(img.texture()),
                            curX, curY[0], 0, 0, img.w(), img.h(), img.w(), img.h());
                    regions.add(new RichSpan.Region(curX, curY[0], curX + img.w(), curY[0] + img.h(), img));
                }
                curY[0] += img.h() + 2;
                curX = originX;
            } else if (span instanceof RichSpan.ItemIcon icon) {
                if (curX + 18 > originX + maxW && curX > originX) {
                    curX = originX;
                    curY[0] += lineH;
                }
                int iconY = curY[0] - (16 - lineH) / 2;
                if (iconY >= clipTop && iconY + 16 <= clipBot) {
                    Item item = ForgeRegistries.ITEMS.getValue(icon.itemId());
                    if (item != null) {
                        try {
                            g.renderItem(new ItemStack(item), curX, iconY);
                        } catch (Exception ignored) {}
                    }
                    regions.add(new RichSpan.Region(curX, iconY, curX + 16, iconY + 16, icon));
                }
                curX += 18;
            } else if (span instanceof RichSpan.Text t) {
                int[] pos = renderWords(g, font, t.text(), t.style(), 0xFFFFFFFF,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, t, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.Link l) {
                Style ls = l.style().withColor(LINK_COLOR).withUnderlined(true);
                int[] pos = renderWords(g, font, l.label(), ls, LINK_COLOR,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, l, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.Tip t) {
                Style ts = t.style().withColor(TIP_COLOR).withUnderlined(true);
                int[] pos = renderWords(g, font, t.label(), ts, TIP_COLOR,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, t, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.ConditionalTip t) {

                Style ts = t.style().withColor(TIP_COLOR);
                int[] pos = renderWords(g, font, t.label(), ts, TIP_COLOR,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, null, scale);
                curX = pos[0];
                curY[0] = pos[1];
            }
        }
        if (curX > originX) curY[0] += lineH;
    }

    public static int measureSpanList(Font font, List<RichSpan> spans, int maxW, float scale) {
        return measureSpanListFrom(font, spans, maxW, 0, scale);
    }

    public static int measureSpanListFrom(Font font, List<RichSpan> spans, int maxW, int startY, float scale) {
        int lineH = Math.round(LINE_H * scale);
        int curX = 0, curY = startY;
        for (RichSpan span : spans) {
            if (span instanceof RichSpan.Image img) {
                if (curX > 0) curY += lineH;
                curY += img.h() + 2;
                curX = 0;
            } else if (span instanceof RichSpan.ItemIcon) {
                if (curX + 18 > maxW && curX > 0) {
                    curX = 0;
                    curY += lineH;
                }
                curX += 18;
            } else if (span instanceof RichSpan.Text t) {
                int[] p = measureWords(font, t.text(), t.style(), curX, curY, 0, maxW, scale * t.scale());
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Link l) {
                int[] p = measureWords(font, l.label(), l.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Tip t) {
                int[] p = measureWords(font, t.label(), t.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.ConditionalTip t) {
                int[] p = measureWords(font, t.label(), t.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            }
        }
        return curY + (curX > 0 ? lineH : 0);
    }

    private static int[] renderWords(GuiGraphics g, Font font,
                                     String text, Style style, int fallbackColor,
                                     int curX, int curY,
                                     int originX, int maxW,
                                     int clipTop, int clipBot,
                                     List<RichSpan.Region> regions, RichSpan source, float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };

        if (source instanceof RichSpan.Text t) scale *= t.scale();

        int lineH = Math.round(LINE_H * scale);
        String inlineCodeText = source instanceof RichSpan.Text t ? t.copyText() : null;
        boolean interactive = source instanceof RichSpan.Link || source instanceof RichSpan.Tip ||
                inlineCodeText != null;
        RichSpan regionPayload = inlineCodeText != null ? new RichSpan.CodeCopy(inlineCodeText) : source;
        int background = source instanceof RichSpan.Text t ? t.background() : 0;

        String[] lines = text.split("\n", -1);
        Style running = style;
        List<String[]> openTags = new ArrayList<>();
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            String line = lines[li];
            String[] tokens = tokenize(line);

            StringBuilder run = new StringBuilder();
            Style runStyle = running;
            int runStartX = curX;

            for (String token : tokens) {
                if (token.isBlank() && curX == originX) continue;
                Style newStyle = applyLeadingLegacyCodes(running, token);
                Style styleAfterToken = applyLegacyCodes(running, token);
                float tokScale = boldScale(scale, newStyle.isBold());
                int tokW = Math.round(font.width(Component.literal(token).withStyle(newStyle)) * tokScale);
                if (curX + tokW > originX + maxW && curX > originX) {
                    flushRunKeepingTags(g, font, run, openTags, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                            boldScale(scale, runStyle.isBold()), background);
                    curX = originX;
                    curY += lineH;
                    runStartX = curX;
                }

                if (!newStyle.equals(runStyle) && !run.isEmpty()) {
                    flushRunKeepingTags(g, font, run, openTags, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                            boldScale(scale, runStyle.isBold()), background);
                    runStartX = curX;
                }
                running = styleAfterToken;
                runStyle = newStyle;

                if (tokW > maxW) {

                    for (int ci = 0; ci < token.length(); ci++) {
                        char ch = token.charAt(ci);
                        int chW = Math.round(font.width(String.valueOf(ch)) * tokScale);
                        if (curX + chW > originX + maxW && curX > originX) {
                            flushRunKeepingTags(g, font, run, openTags, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                                    boldScale(scale, runStyle.isBold()), background);
                            curX = originX;
                            curY += lineH;
                            runStartX = curX;
                        }
                        if (run.isEmpty()) reopenTags(run, openTags);
                        run.append(ch);
                        if (interactive && curY >= clipTop && curY + lineH <= clipBot) {
                            regions.add(new RichSpan.Region(curX, curY, curX + chW, curY + lineH, regionPayload));
                        }
                        curX += chW;
                    }
                    continue;
                }

                if (run.isEmpty()) reopenTags(run, openTags);
                run.append(token);
                trackTags(token, openTags);

                if (interactive && !token.isBlank() && curY >= clipTop && curY + lineH <= clipBot) {
                    regions.add(new RichSpan.Region(curX, curY, curX + tokW, curY + lineH, regionPayload));
                }
                curX += tokW;
            }
            flushRunKeepingTags(g, font, run, openTags, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot,
                    boldScale(scale, runStyle.isBold()), background);
        }
        return new int[] { curX, curY };
    }

    private static final Pattern EFFECT_TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_]*)[^<>\\n]*>");
    private static Boolean textAnimatorLoaded;

    /** Text Animator parses {@code <effect ...>text</effect>} inside whatever string it is asked to draw. */
    private static boolean effectTagsEnabled() {
        if (textAnimatorLoaded == null) {
            ModList mods = ModList.get();
            textAnimatorLoaded = mods != null && mods.isLoaded("textanimator");
        }
        return textAnimatorLoaded;
    }

    /** End index (exclusive) of an effect tag starting at {@code from}, or -1 when there isn't one. */
    private static int effectTagEnd(String s, int from) {
        Matcher m = EFFECT_TAG.matcher(s);
        m.region(from, s.length());
        return m.lookingAt() ? m.end() : -1;
    }

    private static void trackTags(String token, List<String[]> open) {
        if (!effectTagsEnabled() || token.indexOf('<') < 0) return;
        Matcher m = EFFECT_TAG.matcher(token);
        while (m.find()) {
            String name = m.group(2);
            if (!m.group(1).isEmpty()) {
                for (int i = open.size() - 1; i >= 0; i--) {
                    if (open.get(i)[0].equals(name)) {
                        open.remove(i);
                        break;
                    }
                }
            } else {
                open.add(new String[] { name, m.group() });
            }
        }
    }

    /** A line break ends a drawn run, so an effect still open must be re-opened where the next run begins. */
    private static void reopenTags(StringBuilder run, List<String[]> open) {
        for (String[] tag : open) run.append(tag[1]);
    }

    private static void flushRunKeepingTags(GuiGraphics g, Font font, StringBuilder run, List<String[]> open,
                                            Style runStyle, int fallbackColor, int runStartX, int curY, int clipTop,
                                            int clipBot, float scale, int background) {
        if (run.isEmpty()) return;
        for (int i = open.size() - 1; i >= 0; i--) run.append("</").append(open.get(i)[0]).append('>');
        flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot, scale, background);
    }

    private static void flushRun(GuiGraphics g, Font font, StringBuilder run, Style runStyle, int fallbackColor,
                                 int runStartX, int curY, int clipTop, int clipBot, float scale, int background) {
        if (run.isEmpty()) return;
        if (curY >= clipTop && curY + 8 <= clipBot) {
            var comp = Component.literal(run.toString()).withStyle(runStyle);
            int color = runStyle.getColor() != null ? (0xFF000000 | runStyle.getColor().getValue()) : fallbackColor;
            int w = Math.round(font.width(comp) * scale);
            if (background != 0) {
                g.fill(runStartX - 1, curY - 1, runStartX + w + 1, curY + 9, background);
            }
            if (scale == 1.0f) {
                g.drawString(font, comp, runStartX, curY, color, false);
            } else {
                g.pose().pushPose();
                g.pose().translate(runStartX, curY, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, comp, 0, 0, color, false);
                g.pose().popPose();
            }
        }
        run.setLength(0);
    }

    private static int[] measureWords(Font font, String text, Style style, int curX, int curY, int originX, int maxW,
                                      float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };
        int lineH = Math.round(LINE_H * scale);
        String[] lines = text.split("\n", -1);
        Style running = style;
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            for (String token : tokenize(lines[li])) {
                if (token.isBlank() && curX == originX) continue;
                Style startStyle = applyLeadingLegacyCodes(running, token);
                running = applyLegacyCodes(running, token);
                float tokScale = boldScale(scale, startStyle.isBold());
                int tokW = Math.round(font.width(Component.literal(token).withStyle(startStyle)) * tokScale);
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += lineH;
                }
                if (tokW > maxW) {

                    for (int ci = 0; ci < token.length(); ci++) {
                        int chW = Math.round(font.width(String.valueOf(token.charAt(ci))) * tokScale);
                        if (curX + chW > originX + maxW && curX > originX) {
                            curX = originX;
                            curY += lineH;
                        }
                        curX += chW;
                    }
                    continue;
                }
                curX += tokW;
            }
        }
        return new int[] { curX, curY };
    }

    /** The style after every legacy code in the token - what the text following the token should use. */
    private static Style applyLegacyCodes(Style base, String token) {
        return applyLegacyCodes(base, token, token.length());
    }

    /**
     * Only the codes before the token's first visible character. Codes later in the token (a trailing
     * {@code \u00A7r}, say) take effect from where they appear, and the font applies them itself while drawing
     * the token's text - so using them to style the whole token reset the word they trail.
     */
    private static Style applyLeadingLegacyCodes(Style base, String token) {
        int end = 0;
        while (end + 1 < token.length() && token.charAt(end) == '\u00A7' &&
                ChatFormatting.getByCode(token.charAt(end + 1)) != null) {
            end += 2;
        }
        return applyLegacyCodes(base, token, end);
    }

    private static Style applyLegacyCodes(Style base, String token, int limit) {
        Style style = base;
        for (int i = 0; i < limit - 1; i++) {
            if (token.charAt(i) != '\u00A7') continue;
            var fmt = ChatFormatting.getByCode(token.charAt(i + 1));
            if (fmt == null) continue;
            style = switch (fmt) {
                case RESET -> Style.EMPTY;
                case BOLD -> style.withBold(true);
                case ITALIC -> style.withItalic(true);
                case UNDERLINE -> style.withUnderlined(true);
                case STRIKETHROUGH -> style.withStrikethrough(true);
                case OBFUSCATED -> style.withObfuscated(true);
                default -> style.withColor(fmt);
            };
        }
        return style;
    }

    private static String[] tokenize(String s) {
        if (s.isEmpty()) return new String[] { "" };
        List<String> tokens = new ArrayList<>();
        int i = 0, len = s.length();
        boolean keepTags = effectTagsEnabled();
        while (i < len) {
            int start = i;
            while (i < len && s.charAt(i) != ' ') {
                int tagEnd = keepTags && s.charAt(i) == '<' ? effectTagEnd(s, i) : -1;
                i = tagEnd > 0 ? tagEnd : i + 1;
            }
            while (i < len && s.charAt(i) == ' ') i++;
            tokens.add(s.substring(start, i));
        }
        return tokens.toArray(String[]::new);
    }
}

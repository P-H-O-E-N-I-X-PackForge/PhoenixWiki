package net.phoenixvine.wiki.client.screen;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class MultilineTextArea extends AbstractWidget {

    private static final int C_ACCENT = 0xFF00AA55;
    private static final int C_BORDER = 0xFF3A3040;
    private static final int C_SEL_FILL = 0x552255FF;
    private static final int C_SEL_OUTLINE = 0xFF2255FF;
    private static final int C_HOVER_FILL = 0x33AAAAFF;
    private static final int C_HOVER_OUTLINE = 0x88AAAAFF;
    private static final int C_SEARCH_FILL = 0x66FFDD55;
    private static final int C_SEARCH_CURRENT_FILL = 0xAAFFAA00;
    private static final int C_SCROLLBAR_TRACK = 0x33FFFFFF;
    private static final int C_SCROLLBAR_THUMB = 0x99CCCCCC;
    private static final int C_SCROLLBAR_THUMB_ACTIVE = 0xFFFFFFFF;

    private final Font font;
    private final MultilineTextField textField;
    private final int maxLength;
    private final List<LinePos> lines = new ArrayList<>();
    private int hoverWordStart = -1;
    private int hoverWordEnd = -1;
    private int scrollLines = 0;
    private int lastCursorForScroll = -1;
    private Consumer<String> responder;

    private float uiScale = 1f;

    public void setUiScale(float scale) {
        this.uiScale = scale > 0f ? scale : 1f;
    }

    private String lastWrappedText = null;
    private int lastWrapWidth = -1;

    private static final long UNDO_COALESCE_MS = 700;
    private static final int MAX_UNDO = 200;

    private record EditorState(String text, int cursor) {}

    private final Deque<EditorState> undoStack = new ArrayDeque<>();
    private final Deque<EditorState> redoStack = new ArrayDeque<>();
    private long lastUndoPushTime = 0;

    private void withUndoSnapshot(boolean coalesce, Runnable action) {
        String before = textField.value();
        int beforeCursor = textField.cursor();
        action.run();
        if (!textField.value().equals(before)) {
            recordUndoState(before, beforeCursor, coalesce);
        }
    }

    private boolean withUndoSnapshotBool(boolean coalesce, BooleanSupplier action) {
        String before = textField.value();
        int beforeCursor = textField.cursor();
        boolean result = action.getAsBoolean();
        if (!textField.value().equals(before)) {
            recordUndoState(before, beforeCursor, coalesce);
        }
        return result;
    }

    private void recordUndoState(String text, int cursor, boolean coalesce) {
        long now = System.currentTimeMillis();
        if (coalesce && !undoStack.isEmpty() && now - lastUndoPushTime < UNDO_COALESCE_MS) {
            lastUndoPushTime = now;
            return;
        }
        undoStack.push(new EditorState(text, cursor));
        if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        lastUndoPushTime = now;
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        EditorState state = undoStack.pop();
        redoStack.push(new EditorState(textField.value(), textField.cursor()));
        textField.setValue(state.text());
        textField.seekCursor(Whence.ABSOLUTE, Math.min(state.cursor(), state.text().length()));
        lastUndoPushTime = 0;
        fireChanged();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        EditorState state = redoStack.pop();
        undoStack.push(new EditorState(textField.value(), textField.cursor()));
        textField.setValue(state.text());
        textField.seekCursor(Whence.ABSOLUTE, Math.min(state.cursor(), state.text().length()));
        lastUndoPushTime = 0;
        fireChanged();
    }

    private boolean searchActive = false;
    private String searchQuery = "";
    private final List<int[]> searchMatches = new ArrayList<>();
    private int searchMatchIndex = -1;

    public boolean isSearchActive() {
        return searchActive;
    }

    private void toggleSearch() {
        searchActive = !searchActive;
        if (searchActive) {
            updateSearchMatches();
        } else {
            searchMatches.clear();
            searchMatchIndex = -1;
        }
    }

    private void updateSearchMatches() {
        searchMatches.clear();
        searchMatchIndex = -1;
        if (searchQuery.isEmpty()) return;
        String hay = textField.value().toLowerCase();
        String needle = searchQuery.toLowerCase();
        int from = 0;
        while (true) {
            int idx = hay.indexOf(needle, from);
            if (idx < 0) break;
            searchMatches.add(new int[] { idx, idx + needle.length() });
            from = idx + Math.max(1, needle.length());
        }
        if (searchMatches.isEmpty()) return;
        int cursor = textField.cursor();
        int best = 0;
        for (int i = 0; i < searchMatches.size(); i++) {
            if (searchMatches.get(i)[0] >= cursor) {
                best = i;
                break;
            }
        }
        searchMatchIndex = best;
        selectMatch(searchMatchIndex);
    }

    private void jumpToMatch(int dir) {
        if (searchMatches.isEmpty()) return;
        searchMatchIndex = Math.floorMod(searchMatchIndex + dir, searchMatches.size());
        selectMatch(searchMatchIndex);
    }

    private void selectMatch(int idx) {
        int[] m = searchMatches.get(idx);
        textField.setSelecting(false);
        textField.seekCursor(Whence.ABSOLUTE, m[0]);
        textField.setSelecting(true);
        textField.seekCursor(Whence.ABSOLUTE, m[1]);
        textField.setSelecting(false);
    }

    private int[] searchBarBounds() {
        if (!searchActive) return null;
        String label = "Find: " + searchQuery;
        int barW = Math.max(120, font.width(label) + 70);
        int barH = 14;
        int barX = getX() + width - barW - 4;
        int barY = getY() + 4;
        return new int[] { barX, barY, barW, barH };
    }

    private void renderSearchBar(GuiGraphics g) {
        int[] b = searchBarBounds();
        if (b == null) return;
        int barX = b[0], barY = b[1], barW = b[2], barH = b[3];
        g.fill(barX, barY, barX + barW, barY + barH, 0xEE1A1624);
        drawBorder(g, barX, barY, barW, barH, C_ACCENT);
        boolean caretOn = (System.currentTimeMillis() / 530) % 2 == 0;
        g.drawString(font, "Find: " + searchQuery + (caretOn ? "_" : ""), barX + 4, barY + 3, 0xFFFFFFFF, false);
        String countText = searchQuery.isEmpty() ? ""
                : searchMatches.isEmpty() ? "0/0" : (searchMatchIndex + 1) + "/" + searchMatches.size();
        if (!countText.isEmpty()) {
            g.drawString(font, countText, barX + barW - 4 - font.width(countText), barY + 3,
                    searchMatches.isEmpty() ? 0xFFFF6666 : 0xFFAAAAAA, false);
        }
    }

    private static final int SCROLLBAR_HIT_PAD = 3;

    private boolean draggingScrollbar = false;
    private double scrollbarGrabOffset = 0;

    private record ScrollbarGeom(int trackX, int trackTop, int trackH, int thumbH, int thumbY, int maxScroll) {}

    private ScrollbarGeom scrollbarGeom() {
        int visLines = Math.max(1, (height - 6) / 9);
        int maxScroll = Math.max(0, lines.size() - visLines);
        if (maxScroll <= 0) return null;
        int trackX = getX() + width - 3;
        int trackTop = getY() + 2, trackBot = getY() + height - 2;
        int trackH = trackBot - trackTop;
        int thumbH = Math.max(10, trackH * visLines / (visLines + maxScroll));
        int thumbY = trackTop + (int) ((long) scrollLines * (trackH - thumbH) / maxScroll);
        return new ScrollbarGeom(trackX, trackTop, trackH, thumbH, thumbY, maxScroll);
    }

    private boolean isInScrollbarHitZone(double mx, ScrollbarGeom sb) {
        return mx >= sb.trackX() - SCROLLBAR_HIT_PAD && mx < sb.trackX() + 2 + SCROLLBAR_HIT_PAD;
    }

    private void setScrollFromThumbY(double my, ScrollbarGeom sb) {
        double grabbedThumbY = my - scrollbarGrabOffset;
        double range = Math.max(1, sb.trackH() - sb.thumbH());
        double frac = (grabbedThumbY - sb.trackTop()) / range;
        frac = Math.max(0.0, Math.min(1.0, frac));
        scrollLines = (int) Math.round(frac * sb.maxScroll());
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public MultilineTextArea(Font font, int x, int y, int w, int h, int maxLength) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.maxLength = maxLength;
        this.textField = new MultilineTextField(font, w - 12);
        this.textField.setCharacterLimit(maxLength);
    }

    public void setValue(String v) {
        if (v != null) {
            v = v.replace("\r\n", "\n").replace("\r", "\n");
        }
        textField.setValue(v == null ? "" : v);
        undoStack.clear();
        redoStack.clear();
    }

    public void seekToStart() {
        textField.seekCursor(Whence.ABSOLUTE, 0);

        scrollLines = 0;
        lastCursorForScroll = textField.cursor();
    }

    public String getValue() {
        return textField.value();
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    private void fireChanged() {
        if (responder != null) responder.accept(getValue());
    }

    private int[] selectionBounds(String full, String sel) {
        int cursor = textField.cursor();
        int len = sel.length();
        int candidateStart = cursor - len;
        if (candidateStart >= 0 && full.regionMatches(candidateStart, sel, 0, len)) {
            return new int[] { candidateStart, cursor };
        }
        int candidateEnd = cursor + len;
        if (candidateEnd <= full.length() && full.regionMatches(cursor, sel, 0, len)) {
            return new int[] { cursor, candidateEnd };
        }

        int idx = full.indexOf(sel);
        return idx != -1 ? new int[] { idx, idx + len } : new int[] { cursor, cursor };
    }

    public void forceInsert(String text) {
        withUndoSnapshot(false, () -> {
            String full = textField.value();
            int cursor = textField.cursor();
            int start = cursor, end = cursor;
            if (textField.hasSelection()) {
                String sel = textField.getSelectedText();
                int[] bounds = selectionBounds(full, sel);
                start = bounds[0];
                end = bounds[1];
            }
            String updated = full.substring(0, start) + text + full.substring(end);
            if (updated.length() <= maxLength) {
                textField.setValue(updated);
                textField.seekCursor(Whence.ABSOLUTE, start + text.length());
                fireChanged();
            }
        });
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF0A0A10);
        drawBorder(g, getX(), getY(), width, height, isFocused() ? C_ACCENT : C_BORDER);

        int textX = getX() + 6;
        int textY = getY() + 6;
        int cursor = textField.cursor();
        String full = textField.value();
        String disp = full.replace('§', '&');

        if (!disp.equals(lastWrappedText) || width != lastWrapWidth) {
            lines.clear();
            if (disp.isEmpty()) {
                lines.add(new LinePos(0, 0, ""));
            } else {
                int currentIndex = 0;
                String[] rawLines = disp.split("\n", -1);

                for (String rawLine : rawLines) {
                    if (rawLine.isEmpty()) {
                        lines.add(new LinePos(currentIndex, currentIndex, ""));
                    } else {

                        int maxWidth = width - 12;
                        int segStart = 0;
                        int lastBreak = -1;
                        int segW = 0;
                        int i = 0;
                        while (i < rawLine.length()) {
                            int cw = font.width(String.valueOf(rawLine.charAt(i)));
                            if (segW + cw > maxWidth && i > segStart) {
                                int breakAt = lastBreak > segStart ? lastBreak : i;
                                lines.add(new LinePos(currentIndex + segStart, currentIndex + breakAt,
                                        disp.substring(currentIndex + segStart, currentIndex + breakAt)));
                                segStart = breakAt;
                                while (segStart < rawLine.length() && rawLine.charAt(segStart) == ' ') segStart++;
                                i = segStart;
                                segW = 0;
                                lastBreak = -1;
                                continue;
                            }
                            if (rawLine.charAt(i) == ' ') lastBreak = i + 1;
                            segW += cw;
                            i++;
                        }
                        lines.add(new LinePos(currentIndex + segStart, currentIndex + rawLine.length(),
                                disp.substring(currentIndex + segStart, currentIndex + rawLine.length())));
                    }
                    currentIndex += rawLine.length() + 1;
                }
            }
            lastWrappedText = disp;
            lastWrapWidth = width;
        }

        int visibleLines = Math.max(1, (height - 6) / 9);
        int cursorLine = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (cursor >= lines.get(i).start && cursor <= lines.get(i).end) {
                cursorLine = i;
                break;
            }
        }

        if (!draggingScrollbar && cursor != lastCursorForScroll) {
            if (cursorLine < scrollLines) scrollLines = cursorLine;
            if (cursorLine >= scrollLines + visibleLines) scrollLines = cursorLine - visibleLines + 1;
            lastCursorForScroll = cursor;
        }
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollLines = Math.max(0, Math.min(scrollLines, maxScroll));

        updateHoverWord(mx, my, textX, textY, disp);

        g.enableScissor(Math.round(getX() * uiScale), Math.round(getY() * uiScale),
                Math.round((getX() + width) * uiScale), Math.round((getY() + height) * uiScale));

        if (!textField.hasSelection() && hoverWordStart >= 0 && hoverWordEnd > hoverWordStart) {
            for (int i = 0; i < lines.size(); i++) {
                LinePos line = lines.get(i);
                int lineY = textY + (i - scrollLines) * 9;
                if (hoverWordEnd > line.start && hoverWordStart < line.end) {
                    int a = Math.max(hoverWordStart, line.start) - line.start;
                    int b = Math.min(hoverWordEnd, line.end) - line.start;
                    int x1 = textX + font.width(line.text.substring(0, a));
                    int x2 = textX + font.width(line.text.substring(0, b));
                    g.fill(x1, lineY, x2, lineY + 9, C_HOVER_FILL);
                    g.fill(x1, lineY, x2, lineY + 1, C_HOVER_OUTLINE);
                    g.fill(x1, lineY + 8, x2, lineY + 9, C_HOVER_OUTLINE);
                    g.fill(x1, lineY, x1 + 1, lineY + 9, C_HOVER_OUTLINE);
                    g.fill(x2 - 1, lineY, x2, lineY + 9, C_HOVER_OUTLINE);
                }
            }
        }

        if (textField.hasSelection()) {
            String sel = textField.getSelectedText().replace('§', '&');
            int[] bounds = selectionBounds(disp, sel);
            int selStart = bounds[0];
            int selEnd = bounds[1];
            for (int i = 0; i < lines.size(); i++) {
                LinePos line = lines.get(i);
                int lineY = textY + (i - scrollLines) * 9;
                if (selEnd > line.start && selStart < line.end) {
                    int a = Math.max(selStart, line.start) - line.start;
                    int b = Math.min(selEnd, line.end) - line.start;
                    int x1 = textX + font.width(line.text.substring(0, a));
                    int x2 = textX + font.width(line.text.substring(0, b));
                    g.fill(x1, lineY, x2, lineY + 9, C_SEL_FILL);
                    g.fill(x1, lineY, x2, lineY + 1, C_SEL_OUTLINE);
                    g.fill(x1, lineY + 8, x2, lineY + 9, C_SEL_OUTLINE);
                    g.fill(x1, lineY, x1 + 1, lineY + 9, C_SEL_OUTLINE);
                    g.fill(x2 - 1, lineY, x2, lineY + 9, C_SEL_OUTLINE);
                }
            }
        }

        if (searchActive && !searchMatches.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                LinePos line = lines.get(i);
                int lineY = textY + (i - scrollLines) * 9;
                for (int m = 0; m < searchMatches.size(); m++) {
                    int[] match = searchMatches.get(m);
                    if (match[1] > line.start && match[0] < line.end) {
                        int a = Math.max(match[0], line.start) - line.start;
                        int b = Math.min(match[1], line.end) - line.start;
                        int x1 = textX + font.width(line.text.substring(0, a));
                        int x2 = textX + font.width(line.text.substring(0, b));
                        g.fill(x1, lineY, x2, lineY + 9, m == searchMatchIndex ? C_SEARCH_CURRENT_FILL
                                : C_SEARCH_FILL);
                    }
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            LinePos line = lines.get(i);
            int lineY = textY + (i - scrollLines) * 9;

            if (lineY < getY() || lineY + 9 > getY() + height) continue;
            g.drawString(font, line.text, textX, lineY, 0xFFFFFFFF, false);
            if (isFocused() && cursor >= line.start && cursor <= line.end) {
                if ((System.currentTimeMillis() / 530) % 2 == 0) {
                    int off = cursor - line.start;
                    String sub = line.text.substring(0, Math.min(off, line.text.length()));
                    int cx = textX + font.width(sub);
                    g.fill(cx, lineY, cx + 1, lineY + 9, C_ACCENT);
                }
            }
        }
        g.disableScissor();

        ScrollbarGeom sb = scrollbarGeom();
        if (sb != null) {
            g.fill(sb.trackX(), sb.trackTop(), sb.trackX() + 2, sb.trackTop() + sb.trackH(), C_SCROLLBAR_TRACK);
            g.fill(sb.trackX(), sb.thumbY(), sb.trackX() + 2, sb.thumbY() + sb.thumbH(),
                    draggingScrollbar ? C_SCROLLBAR_THUMB_ACTIVE : C_SCROLLBAR_THUMB);
        }

        if (searchActive) {
            renderSearchBar(g);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < getX() || mouseX >= getX() + width || mouseY < getY() || mouseY >= getY() + height) {
            return false;
        }
        return scrollBy(scrollY);
    }

    public boolean scrollBy(double delta) {
        int visibleLines = Math.max(1, (height - 6) / 9);
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollLines = Math.max(0, Math.min(maxScroll, scrollLines - (int) Math.signum(delta)));
        return true;
    }

    private void updateHoverWord(int mx, int my, int textX, int textY, String disp) {
        hoverWordStart = -1;
        hoverWordEnd = -1;
        if (mx < getX() || mx >= getX() + width || my < getY() || my >= getY() + height) return;
        int lineIdx = Math.max(0, Math.min((int) ((my - textY) / 9) + scrollLines, lines.size() - 1));
        if (lineIdx < 0 || lineIdx >= lines.size()) return;
        LinePos line = lines.get(lineIdx);
        int localX = mx - textX;
        int offset = 0;
        while (offset < line.text.length()) {
            if (font.width(line.text.substring(0, offset + 1)) > localX) break;
            offset++;
        }
        int absPos = line.start + offset;
        if (absPos >= disp.length()) return;
        int ws = absPos;
        while (ws > 0 && !Character.isWhitespace(disp.charAt(ws - 1))) ws--;
        int we = absPos;
        while (we < disp.length() && !Character.isWhitespace(disp.charAt(we))) we++;
        if (we > ws) {
            hoverWordStart = ws;
            hoverWordEnd = we;
        }
    }

    private int charIndexAt(double mx, double my) {
        if (lines.isEmpty()) return 0;
        int lineIdx = Math.max(0,
                Math.min((int) ((my - (getY() + 6)) / 9) + scrollLines, lines.size() - 1));
        LinePos line = lines.get(lineIdx);
        int localX = (int) (mx - (getX() + 6));
        int rawOffset = 0;
        while (rawOffset < line.text.length()) {
            if (font.width(line.text.substring(0, rawOffset + 1)) > localX) break;
            rawOffset++;
        }
        return line.start + rawOffset;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height) {
            setFocused(true);

            if (btn == 0) {
                int[] sbar = searchBarBounds();
                if (sbar != null && mx >= sbar[0] && mx < sbar[0] + sbar[2] && my >= sbar[1] && my < sbar[1] + sbar[3]) {
                    return true;
                }

                ScrollbarGeom sb = scrollbarGeom();
                if (sb != null && isInScrollbarHitZone(mx, sb)) {
                    if (my >= sb.thumbY() && my < sb.thumbY() + sb.thumbH()) {
                        draggingScrollbar = true;
                        scrollbarGrabOffset = my - sb.thumbY();
                    } else {
                        draggingScrollbar = true;
                        scrollbarGrabOffset = sb.thumbH() / 2.0;
                        setScrollFromThumbY(my, sb);
                    }
                    return true;
                }
            }

            if (btn == 0 && !lines.isEmpty()) {
                textField.setSelecting(false);
                textField.seekCursor(Whence.ABSOLUTE, charIndexAt(mx, my));
            }
            return true;
        }
        setFocused(false);
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && draggingScrollbar) {
            ScrollbarGeom sb = scrollbarGeom();
            if (sb != null) setScrollFromThumbY(my, sb);
            return true;
        }
        if (btn == 0 && isFocused() && !lines.isEmpty()) {
            textField.setSelecting(true);
            textField.seekCursor(Whence.ABSOLUTE, charIndexAt(mx, my));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (!isFocused()) return false;

        if (Screen.hasControlDown() && kc == GLFW.GLFW_KEY_F) {
            toggleSearch();
            return true;
        }

        if (searchActive) {
            if (kc == GLFW.GLFW_KEY_ESCAPE) {
                toggleSearch();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
                jumpToMatch(Screen.hasShiftDown() ? -1 : 1);
                return true;
            }
            if (kc == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateSearchMatches();
                }
                return true;
            }
            return true;
        }

        if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
            this.forceInsert("\n");
            return true;
        }
        if (Screen.hasControlDown()) {
            if (kc == GLFW.GLFW_KEY_Z && !Screen.hasShiftDown()) {
                undo();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_Y || (kc == GLFW.GLFW_KEY_Z && Screen.hasShiftDown())) {
                redo();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_C) {
                if (textField.hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(textField.getSelectedText());
                }
                return true;
            }
            if (kc == GLFW.GLFW_KEY_X) {
                if (textField.hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(textField.getSelectedText());
                    forceInsert("");
                }
                return true;
            }
            if (kc == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    forceInsert(clip.replace("\r\n", "\n").replace("\r", "\n"));
                }
                return true;
            }
        }
        if (withUndoSnapshotBool(true, () -> textField.keyPressed(kc))) {
            fireChanged();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (!isFocused()) return false;

        if (!StringUtil.isAllowedChatCharacter(ch)) return false;

        if (searchActive) {
            searchQuery += ch;
            updateSearchMatches();
            return true;
        }

        withUndoSnapshot(true, () -> textField.insertText(Character.toString(ch)));
        fireChanged();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}

    private record LinePos(int start, int end, String text) {

    }
}
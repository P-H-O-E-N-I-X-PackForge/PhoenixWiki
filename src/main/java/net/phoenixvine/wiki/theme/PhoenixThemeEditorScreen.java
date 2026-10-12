package net.phoenixvine.wiki.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class PhoenixThemeEditorScreen extends Screen {

    private final Screen parent;
    private final String previewAppName;

    private final List<FieldEntry> fields = new ArrayList<>();
    private final List<SectionLabel> sections = new ArrayList<>();
    private EditBox nameInput;

    private int scrollOffset = 0;

    private int listRowsStartY = 0;

    private record Snap(String bg, String panel, String header, String border, String accent,
                        String text, String dim, String faint, String done, String active,
                        String locked, String ally, String name) {}

    private final Stack<Snap> undoStack = new Stack<>();
    private Snap savedSnap;
    private String lastTrackedName = null;
    private boolean isUndoing = false;

    private boolean confirmActive = false;
    private String pendingAction = null;

    private FieldEntry openPickerField = null;
    private float pickerHue = 0f, pickerSat = 1f, pickerVal = 1f;
    private int pickerX, pickerY;
    private boolean draggingSV = false, draggingHue = false;
    private static final int SV_SIZE = 100;
    private static final int HUE_W = 14;
    private static final int HUE_GAP = 6;
    private static final int PICKER_PAD = 8;

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_DIM, C_FAINT;

    private static final int SIDEBAR_MIN = 185;

    private static final int MIN_CONTENT_W = SIDEBAR_MIN + 260;
    private static final int MIN_CONTENT_H = 442;

    private float uiScale = 1f;
    private int virtualWidth, virtualHeight;

    private long animTick = 0L;

    private static float animPulse(float base, float amplitude, double periodDivisor) {
        if (PhoenixTheme.isReduceMotion()) return base;
        return base + amplitude * (float) Math.sin(System.currentTimeMillis() / periodDivisor);
    }

    private final String ownerModId;

    public PhoenixThemeEditorScreen(Screen parent) {
        this(parent, "Phoenix Suite");
    }

    public PhoenixThemeEditorScreen(Screen parent, String previewAppName) {
        super(Component.literal("Theme Editor"));
        this.parent = parent;
        this.previewAppName = previewAppName;
        this.ownerModId = PhoenixTheme.resolveCallerModId();
    }

    @Override
    protected void init() {
        clearWidgets();
        fields.clear();
        sections.clear();

        uiScale = (width < MIN_CONTENT_W || height < MIN_CONTENT_H) ?
                Math.min((float) width / MIN_CONTENT_W, (float) height / MIN_CONTENT_H) : 1f;
        uiScale = Math.max(0.1f, uiScale);
        virtualWidth = Math.round(width / uiScale);
        virtualHeight = Math.round(height / uiScale);

        var phoenixTheme = PhoenixTheme.current(ownerModId);
        var curName = PhoenixTheme.getActiveName(ownerModId);

        syncPalette(phoenixTheme);

        if (!curName.equals(lastTrackedName)) {
            lastTrackedName = curName;
            savedSnap = makeSnap(phoenixTheme, curName);
            undoStack.clear();
            confirmActive = false;
            pendingAction = null;
        }

        int sbW = sidebarW();
        int sbX = virtualWidth - sbW + 6;

        int boxW = sbW - 97;
        int y = 38;
        int rh = virtualHeight > 360 ? 20 : 17;

        sections.add(new SectionLabel("■ Base Layers", sbX, y));
        y += 12;
        addField("BG", phoenixTheme.bg, sbX, y, boxW);
        y += rh;
        addField("Panel", phoenixTheme.panel, sbX, y, boxW);
        y += rh;
        addField("Header", phoenixTheme.header, sbX, y, boxW);
        y += rh;
        addField("Border", phoenixTheme.border, sbX, y, boxW);
        y += rh + 6;

        sections.add(new SectionLabel("■ Typography", sbX, y));
        y += 12;
        addField("Text", phoenixTheme.text, sbX, y, boxW);
        y += rh;
        addField("Text Dim", phoenixTheme.textDim, sbX, y, boxW);
        y += rh;
        addField("Faint", phoenixTheme.textFaint, sbX, y, boxW);
        y += rh + 6;

        sections.add(new SectionLabel("■ State Colors", sbX, y));
        y += 12;
        addField("Accent", phoenixTheme.accent, sbX, y, boxW);
        y += rh;
        addField("Done", phoenixTheme.done, sbX, y, boxW);
        y += rh;
        addField("Active", phoenixTheme.activeColor, sbX, y, boxW);
        y += rh;
        addField("Locked", phoenixTheme.locked, sbX, y, boxW);
        y += rh;
        addField("Ally", phoenixTheme.ally, sbX, y, boxW);
        y += rh + 6;

        int ctrlY = Math.max(y + 4, virtualHeight - 114);
        nameInput = new EditBox(font, virtualWidth - sbW + 10, ctrlY, sbW - 20, 16, Component.literal("Theme name"));
        nameInput.setValue(curName);
        nameInput.setMaxLength(32);
        nameInput.setResponder(s -> confirmActive = false);
        addWidget(nameInput);

        int halfW = (sbW - 20 - 4) / 2;
        addRenderableWidget(Button
                .builder(Component.literal("§aSave"), b -> save())
                .bounds(virtualWidth - sbW + 10, ctrlY + 20, halfW, 18)
                .tooltip(Tooltip.create(Component.literal(
                        "Saves the current colors under the name above. Overwrites that theme if it\n" +
                                "already exists (only ever a custom one, never a built-in).")))
                .build());
        addRenderableWidget(Button
                .builder(Component.literal("§b+ New"), b -> newTheme())
                .bounds(virtualWidth - sbW + 10 + halfW + 4, ctrlY + 20, sbW - 20 - halfW - 4, 18)
                .tooltip(Tooltip.create(Component.literal(
                        "Starts a brand-new custom theme (a copy of the current colors) under its own\n" +
                                "name, so editing it never touches whatever theme you started from.")))
                .build());

        addRenderableWidget(Button
                .builder(Component.literal("🎲 Reroll"), b -> reroll())
                .bounds(virtualWidth - sbW + 10, ctrlY + 42, sbW - 20, 18)
                .tooltip(Tooltip.create(Component.literal(
                        "Randomizes every color and gives it a silly name.\n" +
                                "Ctrl+Z to revert if it's too much.")))
                .build());

        addRenderableWidget(Button
                .builder(Component.literal("§7Exit"), b -> {
                    if (hasChanges()) {
                        if (!confirmActive || !"EXIT".equals(pendingAction)) {
                            confirmActive = true;
                            pendingAction = "EXIT";
                            return;
                        }
                        restore(savedSnap);
                    }
                    onClose();
                })
                .bounds(virtualWidth - sbW + 10, ctrlY + 64, sbW - 20, 18).build());

        addRenderableWidget(Button
                .builder(Component.literal(PhoenixTheme.isSharedMode() ?
                        "§7Mode: §fShared suite-wide" : "§7Mode: §fPer-mod"),
                        b -> {
                            PhoenixTheme.setSharedMode(!PhoenixTheme.isSharedMode());
                            lastTrackedName = null;
                            init();
                        })
                .bounds(virtualWidth - sbW + 10, ctrlY + 86, sbW - 20, 18)
                .tooltip(Tooltip.create(Component.literal(
                        "Shared: every Phoenix suite mod uses this one theme.\n" +
                                "Per-mod: each mod remembers its own. The theme you set here only\n" +
                                "applies to whichever mod opened this screen.")))
                .build());
    }

    private static final String[] REROLL_WORDS = {
            "Regrettable", "Illegally", "Suspicious", "Mildly", "Aggressively", "Cursed",
            "Deeply", "Questionably", "Unreasonably", "Tragically", "Neon", "Bright", "Beige",
            "Radioactive", "Chaos", "Contrast", "Sunset", "Static", "Glorious", "Haunted",
    };

    private void reroll() {
        pushUndo();
        var rng = new java.util.Random();
        for (FieldEntry f : fields) {
            float hue = rng.nextFloat();
            float sat = 0.45f + rng.nextFloat() * 0.45f;
            float val = 0.35f + rng.nextFloat() * 0.55f;
            String hex = String.format("FF%06X", Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF);
            f.target().set(hex);
            f.box().setValue(hex);
        }
        if (nameInput != null) {
            String name = REROLL_WORDS[rng.nextInt(REROLL_WORDS.length)] + " " +
                    REROLL_WORDS[rng.nextInt(REROLL_WORDS.length)];
            nameInput.setValue(name.toUpperCase(Locale.ROOT));
        }
        confirmActive = false;
        syncPalette(PhoenixTheme.current(ownerModId));
    }

    private void newTheme() {
        PhoenixTheme.createNewTheme(ownerModId, "CUSTOM", PhoenixTheme.current(ownerModId));
        confirmActive = false;
        pendingAction = null;
        lastTrackedName = null;
        init();
    }

    private void openColorPicker(FieldEntry f) {
        openPickerField = f;
        int argb = f.target().getColor();
        float[] hsv = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        pickerHue = hsv[0];
        pickerSat = hsv[1];
        pickerVal = hsv[2];

        int totalW = SV_SIZE + HUE_GAP + HUE_W + PICKER_PAD * 2;
        int totalH = SV_SIZE + PICKER_PAD * 2 + 20;
        pickerX = Mth.clamp(f.box().getX(), 4, Math.max(4, virtualWidth - totalW - 4));
        pickerY = Mth.clamp(f.box().getY() + 18, 4, Math.max(4, virtualHeight - totalH - 4));
    }

    private void closeColorPicker() {
        openPickerField = null;
        draggingSV = false;
        draggingHue = false;
    }

    private void applyPickerColor() {
        if (openPickerField == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0xFFFFFF;
        String hex = String.format("FF%06X", rgb);
        openPickerField.target().set(hex);
        openPickerField.box().setValue(hex);
        confirmActive = false;
        syncPalette(PhoenixTheme.current(ownerModId));
    }

    private void updatePickerSV(double mx, double my, int svX, int svY) {
        pickerSat = Mth.clamp((float) (mx - svX) / (SV_SIZE - 1), 0f, 1f);
        pickerVal = 1f - Mth.clamp((float) (my - svY) / (SV_SIZE - 1), 0f, 1f);
        applyPickerColor();
    }

    private void updatePickerHue(double my, int svY) {
        pickerHue = Mth.clamp((float) (my - svY) / (SV_SIZE - 1), 0f, 1f);
        applyPickerColor();
    }

    private void addField(String label, PhoenixTheme.ThemeColor target, int sbX, int y, int boxW) {
        EditBox box = new EditBox(font, sbX + 68, y, boxW, 16, Component.literal(label));
        box.setMaxLength(10);
        box.setValue(target.hex != null ? target.hex.toUpperCase(Locale.ROOT) : "FF000000");
        box.setResponder(v -> {
            if (!isUndoing) pushUndo();
            target.set(v);
            confirmActive = false;
            syncPalette(PhoenixTheme.current(ownerModId));
        });
        addWidget(box);
        fields.add(new FieldEntry(label, target, box));
    }

    private void syncPalette(PhoenixTheme t) {
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_ACCENT = t.accent.getColor();
        C_TEXT = t.text.getColor();
        C_DIM = t.textDim.getColor();
        C_FAINT = t.textFaint.getColor();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int rmx, int rmy, float partial) {
        animTick = PhoenixTheme.isReduceMotion() ? 0L : System.currentTimeMillis();

        int mx = Math.round(rmx / uiScale);
        int my = Math.round(rmy / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int sbW = sidebarW();

        int bgTop = C_BG;
        int bgBot = blend(C_BG, 0xFF000000, 0.35f);
        g.fillGradient(0, 0, virtualWidth - sbW, virtualHeight, bgTop, bgBot);
        g.fill(virtualWidth - sbW, 0, virtualWidth, virtualHeight, C_PANEL);
        g.fill(virtualWidth - sbW, 0, virtualWidth - sbW + 1, virtualHeight, C_BORDER);

        g.fill(virtualWidth - sbW, 0, virtualWidth, 28, C_HEADER);
        float headerPulse = animPulse(0.7f, 0.3f, 900.0);
        int headerAccent = (Math.min(255, (int) (0xFF * headerPulse)) << 24) | (C_ACCENT & 0xFFFFFF);
        g.fill(virtualWidth - sbW, 27, virtualWidth, 28, headerAccent);
        g.drawString(font, "§fTheme Editor", virtualWidth - sbW + 8, 8, C_ACCENT, false);

        String status;
        int statusC;
        if (confirmActive) {
            status = "§eClick again to discard!";
            statusC = 0xFFFFBB33;
        } else if (hasChanges()) {
            status = "§7● " + PhoenixTheme.getActiveName(ownerModId) + " (unsaved)";
            statusC = 0xFFFFBB33;
        } else {
            status = "§7○ " + PhoenixTheme.getActiveName(ownerModId);
            statusC = C_DIM;
        }
        g.drawString(font, status, virtualWidth - sbW + 8, 19, statusC, false);

        for (SectionLabel s : sections) {
            g.drawString(font, "§8" + s.title, s.x, s.y, C_ACCENT, false);
        }
        for (FieldEntry f : fields) {

            g.drawString(font, f.label, f.box.getX() - 65, f.box.getY() + 4, C_TEXT, false);

            int sx = f.box.getX() + f.box.getWidth() + 3;
            int sy = f.box.getY();
            int sw = 14;

            g.fill(sx, sy, sx + sw, sy + 14, f.target.getColor());

            g.fill(sx, sy, sx + sw, sy + 1, C_BORDER);
            g.fill(sx, sy + 13, sx + sw, sy + 14, C_BORDER);
            g.fill(sx, sy, sx + 1, sy + 14, C_BORDER);
            g.fill(sx + sw - 1, sy, sx + sw, sy + 14, C_BORDER);

            f.box.render(g, mx, my, partial);
        }
        if (nameInput != null) nameInput.render(g, mx, my, partial);

        renderPreview(g, mx, my, sbW);

        super.render(g, mx, my, partial);

        if (openPickerField != null) renderColorPicker(g);

        g.pose().popPose();
    }

    private void renderColorPicker(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        g.flush();

        int totalW = SV_SIZE + HUE_GAP + HUE_W + PICKER_PAD * 2;
        int totalH = SV_SIZE + PICKER_PAD * 2 + 20;
        g.fill(pickerX, pickerY, pickerX + totalW, pickerY + totalH, 0xF00A0A0E);
        drawBorder(g, pickerX, pickerY, totalW, totalH, C_BORDER);

        int svX = pickerX + PICKER_PAD, hueY = pickerY + PICKER_PAD;
        int cell = 2;
        for (int px = 0; px < SV_SIZE; px += cell) {
            float s = px / (float) (SV_SIZE - 1);
            for (int py = 0; py < SV_SIZE; py += cell) {
                float v = 1f - py / (float) (SV_SIZE - 1);
                int rgb = Color.HSBtoRGB(pickerHue, s, v);
                g.fill(svX + px, hueY + py, svX + px + cell, hueY + py + cell, 0xFF000000 | (rgb & 0xFFFFFF));
            }
        }
        drawBorder(g, svX, hueY, SV_SIZE, SV_SIZE, C_BORDER);

        int cursorX = svX + Math.round(pickerSat * (SV_SIZE - 1));
        int cursorY = hueY + Math.round((1f - pickerVal) * (SV_SIZE - 1));
        drawBorder(g, cursorX - 3, cursorY - 3, 6, 6, pickerVal > 0.5f ? 0xFF000000 : 0xFFFFFFFF);

        int hueX = svX + SV_SIZE + HUE_GAP;
        for (int py = 0; py < SV_SIZE; py++) {
            float h = py / (float) (SV_SIZE - 1);
            int rgb = Color.HSBtoRGB(h, 1f, 1f);
            g.fill(hueX, hueY + py, hueX + HUE_W, hueY + py + 1, 0xFF000000 | (rgb & 0xFFFFFF));
        }
        drawBorder(g, hueX, hueY, HUE_W, SV_SIZE, C_BORDER);
        int hueMarkerY = hueY + Math.round(pickerHue * (SV_SIZE - 1));
        g.fill(hueX - 2, hueMarkerY - 1, hueX + HUE_W + 2, hueMarkerY, 0xFFFFFFFF);
        g.fill(hueX - 2, hueMarkerY, hueX + HUE_W + 2, hueMarkerY + 1, 0xFF000000);

        int previewY = hueY + SV_SIZE + 4;
        int rgbNow = java.awt.Color.HSBtoRGB(pickerHue, pickerSat, pickerVal);
        g.fill(svX, previewY, svX + totalW - PICKER_PAD * 2, previewY + 12, 0xFF000000 | (rgbNow & 0xFFFFFF));
        drawBorder(g, svX, previewY, totalW - PICKER_PAD * 2, 12, C_BORDER);
        String hex = String.format("#%06X", rgbNow & 0xFFFFFF);
        int hexTextColor = (0.299f * ((rgbNow >> 16) & 0xFF) + 0.587f * ((rgbNow >> 8) & 0xFF) +
                0.114f * (rgbNow & 0xFF)) > 140 ? 0xFF000000 : 0xFFFFFFFF;
        g.drawCenteredString(font, hex, svX + (totalW - PICKER_PAD * 2) / 2, previewY + 2, hexTextColor);

        g.flush();
        g.pose().popPose();
    }

    private void renderPreview(GuiGraphics g, int mx, int my, int sbW) {
        var theme = PhoenixTheme.current(ownerModId);
        int canvasW = virtualWidth - sbW;
        int mockW = Math.min(canvasW - 20, 360);
        int mockX = 10;

        int animY = 8;

        g.drawString(font, "§7State colors · accent line · text hierarchy", mockX, animY, C_FAINT, false);

        int mockTop = animY + 13;
        int mockH = Math.min(150, (virtualHeight - 20) / 2);

        g.fill(mockX, mockTop, mockX + mockW, mockTop + mockH, theme.bg.getColor());
        drawBorder(g, mockX, mockTop, mockW, mockH, theme.border.getColor());

        int hdrH = 17;
        g.fill(mockX, mockTop, mockX + mockW, mockTop + hdrH, theme.header.getColor());
        g.fill(mockX, mockTop + hdrH - 1, mockX + mockW, mockTop + hdrH, theme.border.getColor());
        g.drawString(font, "§f✦ " + previewAppName, mockX + 5, mockTop + 5, theme.text.getColor(), false);

        int sideW = Math.min(52, mockW / 5);
        g.fill(mockX, mockTop + hdrH, mockX + sideW, mockTop + mockH, theme.panel.getColor());
        g.fill(mockX + sideW, mockTop + hdrH, mockX + sideW + 1, mockTop + mockH, theme.border.getColor());
        int navBarW = Math.max(4, sideW - 12);
        g.fill(mockX + 4, mockTop + hdrH + 4, mockX + 4 + navBarW, mockTop + hdrH + 9, theme.textDim.getColor());
        g.fill(mockX + 4, mockTop + hdrH + 15, mockX + 4 + navBarW, mockTop + hdrH + 20, theme.textFaint.getColor());
        g.fill(mockX + 4, mockTop + hdrH + 26, mockX + 4 + navBarW, mockTop + hdrH + 31, theme.textFaint.getColor());

        int sz = Math.max(16, Math.min(26, (mockW - sideW - 30) / 6));
        int n1x = mockX + sideW + 14;
        int n2x = n1x + sz * 2 + 14;
        int n3x = n2x + sz * 2 + 14;
        int ny = mockTop + hdrH + (mockH - hdrH) / 2 - sz / 2;

        int lineY = ny + sz / 2;
        drawMockLine(g, n1x + sz, lineY, n2x, lineY, theme.done.getColor());
        drawMockLine(g, n2x + sz, lineY, n3x, lineY,
                (theme.locked.getColor() & 0x00FFFFFF) | 0x66000000);

        if (!PhoenixTheme.isReduceMotion()) {
            float sparkT = (float) ((animTick / 900.0) % 1.0);
            int sparkX = n1x + sz + (int) (sparkT * (n2x - (n1x + sz)));
            int sparkA = Math.min(255, (int) (0xFF * animPulse(0.75f, 0.25f, 200.0)));
            g.fill(sparkX - 1, lineY - 2, sparkX + 2, lineY + 3,
                    (sparkA << 24) | (theme.activeColor.getColor() & 0xFFFFFF));
        }

        g.fill(n1x, ny, n1x + sz, ny + sz, (theme.done.getColor() & 0x00FFFFFF) | 0xFF081A0E);
        drawBorder(g, n1x, ny, sz, sz, theme.done.getColor());
        g.drawCenteredString(font, "§a✔", n1x + sz / 2, ny + sz / 2 - 4, theme.done.getColor());

        g.fill(n2x, ny, n2x + sz, ny + sz, (theme.activeColor.getColor() & 0x00FFFFFF) | 0xFF221C00);
        float activePulse = animPulse(0.6f, 0.4f, 500.0);
        int glowA = Math.min(255, (int) (0x55 * activePulse));
        int glowColor = (glowA << 24) | (theme.activeColor.getColor() & 0xFFFFFF);
        drawBorder(g, n2x - 2, ny - 2, sz + 4, sz + 4, glowColor);
        drawBorder(g, n2x, ny, sz, sz, theme.activeColor.getColor());
        g.drawCenteredString(font, "§e◎", n2x + sz / 2, ny + sz / 2 - 4, theme.activeColor.getColor());

        g.fill(n3x, ny, n3x + sz, ny + sz, (theme.locked.getColor() & 0x00FFFFFF) | 0xFF1A1A24);
        drawBorder(g, n3x, ny, sz, sz, theme.locked.getColor());
        g.fill(n3x + 1, ny + 1, n3x + sz - 1, ny + sz - 1, 0x880B0B0F);
        g.drawCenteredString(font, "§8✕", n3x + sz / 2, ny + sz / 2 - 4, theme.locked.getColor());

        int lblY = ny + sz + 3;
        if (lblY + 8 < mockTop + mockH) {
            g.drawCenteredString(font, "§8Done", n1x + sz / 2, lblY, theme.textFaint.getColor());
            g.drawCenteredString(font, "§8Active", n2x + sz / 2, lblY, theme.textFaint.getColor());
            g.drawCenteredString(font, "§8Locked", n3x + sz / 2, lblY, theme.textFaint.getColor());
        }

        int swY = mockTop + mockH - 7;
        if (swY > mockTop + hdrH + 4) {
            int sw = Math.max(4, (mockW - sideW - 10) / 5);
            int sx = mockX + sideW + 5;
            g.fill(sx, swY, sx + sw, swY + 5, theme.accent.getColor());
            g.fill(sx + sw + 2, swY, sx + sw * 2 + 2, swY + 5, theme.text.getColor());
            g.fill(sx + sw * 2 + 4, swY, sx + sw * 3 + 4, swY + 5, theme.textDim.getColor());
            g.fill(sx + sw * 3 + 6, swY, sx + sw * 4 + 6, swY + 5, theme.textFaint.getColor());
        }

        int listY = mockTop + mockH + 8;
        g.drawString(font, "§8Themes  §7(click to swap)", mockX, listY, C_FAINT, false);
        listY += 12;
        listRowsStartY = listY;

        List<String> vis = visibleThemes();
        int itemH = 14;
        int maxVis = Math.max(1, (virtualHeight - listY - 6) / itemH);
        int maxScroll = Math.max(0, vis.size() - maxVis);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = scrollOffset; i < vis.size() && listY + itemH <= virtualHeight - 4; i++) {
            String name = vis.get(i);
            boolean sel = name.equals(PhoenixTheme.getActiveName(ownerModId));
            boolean hov = mx >= mockX && mx <= mockX + mockW && my >= listY && my < listY + itemH;

            int rowBg = sel ? ((theme.accent.getColor() & 0x00FFFFFF) | 0x33000000) :
                    hov ? ((theme.border.getColor() & 0x00FFFFFF) | 0x22000000) : 0;
            if (rowBg != 0) g.fill(mockX, listY, mockX + mockW, listY + itemH, rowBg);
            drawBorder(g, mockX, listY, mockW, itemH, (theme.border.getColor() & 0x00FFFFFF) | 0x44000000);

            int nameColor = sel ? theme.accent.getColor() : hov ? theme.text.getColor() : theme.textDim.getColor();
            g.drawString(font, (sel ? "●" : "○") + " " + name, mockX + 6, listY + 3, nameColor, false);

            if (!PhoenixTheme.isBuiltin(name)) {
                int dX = mockX + mockW - 14;
                boolean dHov = mx >= dX && mx <= dX + 12 && my >= listY && my < listY + itemH;
                g.drawString(font, "✕", dX + 1, listY + 3, dHov ? 0xFFFF5555 : 0x66FF5555, false);
            }

            listY += itemH;
        }
    }

    @Override
    public boolean mouseClicked(double rmx, double rmy, int btn) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;

        if (btn != 0) return super.mouseClicked(mx, my, btn);

        if (openPickerField != null) {
            int svX = pickerX + PICKER_PAD, svY = pickerY + PICKER_PAD;
            int hueX = svX + SV_SIZE + HUE_GAP;
            if (mx >= svX && mx < svX + SV_SIZE && my >= svY && my < svY + SV_SIZE) {
                pushUndo();
                draggingSV = true;
                updatePickerSV(mx, my, svX, svY);
                return true;
            }
            if (mx >= hueX - 2 && mx < hueX + HUE_W + 2 && my >= svY && my < svY + SV_SIZE) {
                pushUndo();
                draggingHue = true;
                updatePickerHue(my, svY);
                return true;
            }
            int totalW = SV_SIZE + HUE_GAP + HUE_W + PICKER_PAD * 2;
            int totalH = SV_SIZE + PICKER_PAD * 2 + 20;
            if (mx < pickerX || mx >= pickerX + totalW || my < pickerY || my >= pickerY + totalH) {
                closeColorPicker();
                return true;
            }
            return true;
        }

        for (FieldEntry f : fields) {
            int sx = f.box().getX() + f.box().getWidth() + 3;
            int sy = f.box().getY();
            if (mx >= sx && mx < sx + 14 && my >= sy && my < sy + 14) {
                openColorPicker(f);
                return true;
            }
        }

        int sbW = sidebarW();
        int canvasW = virtualWidth - sbW;
        int mockW = Math.min(canvasW - 20, 360);
        int mockX = 10;
        int itemH = 14;

        List<String> vis = visibleThemes();
        int listY = listRowsStartY;

        for (int i = scrollOffset; i < vis.size() && listY + itemH <= virtualHeight - 4; i++) {
            String name = vis.get(i);
            if (my >= listY && my < listY + itemH) {

                if (!PhoenixTheme.isBuiltin(name)) {
                    int dX = mockX + mockW - 14;
                    if (mx >= dX && mx <= dX + 12) {
                        PhoenixTheme.deleteCustom(name);
                        confirmActive = false;
                        pendingAction = null;
                        lastTrackedName = null;
                        init();
                        return true;
                    }
                }

                if (mx >= mockX && mx <= mockX + mockW - 16) {
                    if (hasChanges()) {
                        if (!confirmActive || !name.equals(pendingAction)) {
                            confirmActive = true;
                            pendingAction = name;
                            return true;
                        }
                        restore(savedSnap);
                    }
                    PhoenixTheme.setCurrent(ownerModId, name);
                    confirmActive = false;
                    pendingAction = null;
                    lastTrackedName = null;
                    init();
                    return true;
                }
            }
            listY += itemH;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double rmx, double rmy, int btn, double dragX, double dragY) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;
        if (draggingSV || draggingHue) {
            int svX = pickerX + PICKER_PAD, svY = pickerY + PICKER_PAD;
            if (draggingSV) updatePickerSV(mx, my, svX, svY);
            else updatePickerHue(my, svY);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double rmx, double rmy, int btn) {
        draggingSV = false;
        draggingHue = false;
        return super.mouseReleased(rmx / uiScale, rmy / uiScale, btn);
    }

    @Override
    public boolean mouseScrolled(double rmx, double rmy, double scrollX, double scrollY) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;
        int sbW = sidebarW();

        if (mx < virtualWidth - sbW) {
            List<String> vis = visibleThemes();
            int itemH = 14;
            int maxVis = Math.max(1, (virtualHeight - listRowsStartY - 6) / itemH);
            int maxScroll = Math.max(0, vis.size() - maxVis);

            scrollOffset = Mth.clamp(scrollOffset - (int) scrollY, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && openPickerField != null) {
            closeColorPicker();
            return true;
        }
        if (hasControlDown()) {
            if (key == 90) {
                tryUndo();
                return true;
            }
            if (key == 83) {
                save();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    private void save() {
        String name = nameInput != null ? nameInput.getValue().trim().toUpperCase(Locale.ROOT) : "";
        if (name.isEmpty()) return;
        var copy = PhoenixTheme.current(ownerModId).copy();
        PhoenixTheme.saveCustomTheme(name, copy);
        PhoenixTheme.setCurrent(ownerModId, name);
        savedSnap = makeSnap(PhoenixTheme.current(ownerModId), name);
        confirmActive = false;
        pendingAction = null;
        lastTrackedName = name;
        init();
    }

    private void pushUndo() {
        Snap cur = makeSnap(PhoenixTheme.current(ownerModId),
                nameInput != null ? nameInput.getValue() : PhoenixTheme.getActiveName(ownerModId));
        if (undoStack.isEmpty() || !undoStack.peek().equals(cur)) {
            undoStack.push(cur);
            if (undoStack.size() > 50) undoStack.remove(0);
        }
    }

    private void tryUndo() {
        if (undoStack.isEmpty()) return;
        isUndoing = true;
        Snap prev = undoStack.pop();
        restore(prev);

        for (FieldEntry f : fields) {
            String v = fieldValue(prev, f.label);
            if (v != null) f.box.setValue(v.toUpperCase(Locale.ROOT));
        }
        if (nameInput != null) nameInput.setValue(prev.name());
        confirmActive = false;
        isUndoing = false;
    }

    private String fieldValue(Snap s, String label) {
        return switch (label) {
            case "BG" -> s.bg();
            case "Panel" -> s.panel();
            case "Header" -> s.header();
            case "Border" -> s.border();
            case "Accent" -> s.accent();
            case "Text" -> s.text();
            case "Text Dim" -> s.dim();
            case "Faint" -> s.faint();
            case "Done" -> s.done();
            case "Active" -> s.active();
            case "Locked" -> s.locked();
            case "Ally" -> s.ally();
            default -> null;
        };
    }

    private boolean hasChanges() {
        if (savedSnap == null) return false;
        return !savedSnap.equals(makeSnap(PhoenixTheme.current(ownerModId),
                nameInput != null ? nameInput.getValue() : PhoenixTheme.getActiveName(ownerModId)));
    }

    private Snap makeSnap(PhoenixTheme t, String name) {
        return new Snap(t.bg.hex, t.panel.hex, t.header.hex, t.border.hex, t.accent.hex,
                t.text.hex, t.textDim.hex, t.textFaint.hex, t.done.hex,
                t.activeColor.hex, t.locked.hex, t.ally.hex, name);
    }

    private void restore(Snap s) {
        PhoenixTheme t = PhoenixTheme.current(ownerModId);
        t.bg.set(s.bg());
        t.panel.set(s.panel());
        t.header.set(s.header());
        t.border.set(s.border());
        t.accent.set(s.accent());
        t.text.set(s.text());
        t.textDim.set(s.dim());
        t.textFaint.set(s.faint());
        t.done.set(s.done());
        t.activeColor.set(s.active());
        t.locked.set(s.locked());
        t.ally.set(s.ally());
        syncPalette(t);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private int sidebarW() {
        return Math.max(SIDEBAR_MIN, virtualWidth / 4);
    }

    private List<String> visibleThemes() {
        return new ArrayList<>(PhoenixTheme.REGISTRY.keySet());
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void drawMockLine(GuiGraphics g, int x1, int y, int x2, int y2, int color) {
        for (int x = x1; x < x2; x++) g.fill(x, y - 1, x + 1, y + 2, color);
    }

    private static int blend(int a, int b, float t) {
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t), rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t), rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private record FieldEntry(String label, PhoenixTheme.ThemeColor target, EditBox box) {}

    private record SectionLabel(String title, int x, int y) {}
}

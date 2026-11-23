package net.phoenixvine.wiki.client.suite;

import net.phoenixvine.wiki.client.WikiText;
import net.phoenixvine.wiki.api.WikiLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.List;
import java.util.function.Consumer;

public class SuiteHudSettingsScreen extends Screen {

    private final Screen parent;

    private static final int ROW_H = 20;
    private static final int PANEL_W = 300;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 50;
    private static final int TOGGLE_ROWS = 1;

    private int panelX, panelY, panelW, panelH;
    /** Per-mod rows are scrolled with the mouse wheel when the screen is too short to show them all. */
    private int scrollRow = 0;
    private int visibleRows = 1;
    private int rowCount = 0;

    public SuiteHudSettingsScreen(Screen parent) {
        super(WikiLang.tr("phoenix_wiki.suite_hud_settings_screen.suite_hud_settings"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        List<String> modIds = SuiteHudBar.getRegisteredModIds();
        panelW = Math.max(220, Math.min(PANEL_W, this.width - 16));
        int fixedH = HEADER_H + TOGGLE_ROWS * ROW_H + 4 + FOOTER_H;
        int maxRows = Math.max(1, (this.height - 16 - fixedH) / ROW_H);
        rowCount = modIds.size();
        visibleRows = Math.max(1, Math.min(Math.max(1, rowCount), maxRows));
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rowCount - visibleRows)));
        panelH = fixedH + visibleRows * ROW_H;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);

        int y = panelY + HEADER_H - ROW_H;

        int scaleLabelW = this.font.width(WikiText.str("phoenix_wiki.suite_hud_settings.suite_wide_scale"));
        int scaleSliderW = Math.max(60, Math.min(130, panelW - 32 - scaleLabelW - 6));
        this.addRenderableWidget(new ScaleSlider(panelX + panelW - 20 - scaleSliderW, y, scaleSliderW, 16,
                SuiteHudConfig.getGlobalScale(), SuiteHudConfig::setGlobalScale));
        y += ROW_H + 4;

        int gap = 4;
        int tw = (panelW - 24 - 2 * gap) / 3;
        int tx = panelX + 12;
        this.addRenderableWidget(toggle(tx, y, tw, "phoenix_wiki.suite_hud_settings.hud_suite",
                SuiteHudConfig.isHudEnabled(), SuiteHudConfig::setHudEnabled,
                "phoenix_wiki.suite_hud_settings.hud_suite_tooltip"));
        this.addRenderableWidget(toggle(tx + tw + gap, y, tw, "phoenix_wiki.suite_hud_settings.transparent_buttons",
                SuiteHudConfig.isTransparentButtons(), SuiteHudConfig::setTransparentButtons,
                "phoenix_wiki.suite_hud_settings.transparent_buttons_tooltip"));
        this.addRenderableWidget(toggle(tx + 2 * (tw + gap), y, tw, "phoenix_wiki.suite_hud_settings.easter_eggs",
                SuiteHudConfig.isEasterEggsEnabled(), SuiteHudConfig::setEasterEggsEnabled,
                "phoenix_wiki.suite_hud_settings.easter_eggs_tooltip"));
        y += ROW_H;

        if (modIds.isEmpty()) {
            y += ROW_H;
        }

        for (String modId : visibleMods(modIds)) {
            Component tip = SuiteHudBar.getTooltip(modId);
            String name = tip != null ? tip.getString().replaceAll("§.", "") : modId;
            name = fit(name, toggleX() - panelX - 16);

            final int rowY = y;
            boolean enabled = SuiteHudBar.isButtonEnabled(modId);
            this.addRenderableWidget(Button.builder(
                    Component.literal(enabled ? "§aON" : WikiText.str("phoenix_wiki.suite_hud_settings.off")),
                    b -> {
                        SuiteHudBar.setButtonEnabled(modId, !SuiteHudBar.isButtonEnabled(modId));
                        this.init();
                    })
                    .bounds(toggleX(), rowY, 34, 16)
                    .tooltip(Tooltip.create(WikiLang.tr("phoenix_wiki.suite_hud_settings_screen.show_hide_this_button_in")))
                    .build());

            Float override = SuiteHudConfig.getButtonScaleOverride(modId);
            float sliderValue = override != null ? override : SuiteHudConfig.getGlobalScale();
            this.addRenderableWidget(new ScaleSlider(toggleX() + 38, rowY, panelX + panelW - 40 - (toggleX() + 38), 16, sliderValue,
                    v -> SuiteHudConfig.setButtonScale(modId, v)));

            this.addRenderableWidget(Button.builder(Component.literal("↻"), b -> {
                        SuiteHudConfig.setButtonScale(modId, null);
                        this.init();
                    })
                    .bounds(panelX + panelW - 36, rowY, 16, 16)
                    .tooltip(Tooltip.create(Component.literal(override != null ? WikiText.str("phoenix_wiki.suite_hud_settings.reset_to_the_suite_wide") : WikiText.str("phoenix_wiki.suite_hud_settings.already_following_the_suite_wide"))))
                    .build());

            y += ROW_H;
        }

        this.addRenderableWidget(Button.builder(Component.literal(SuiteHudBar.isGravityMode() ? WikiText.str("phoenix_wiki.suite_hud_settings.gravity_mode_on") : WikiText.str("phoenix_wiki.suite_hud_settings.gravity_mode_off")),
                        b -> {
                            SuiteHudBar.setGravityMode(!SuiteHudBar.isGravityMode());
                            this.init();
                        })
                .bounds(panelX + 20, panelY + panelH - FOOTER_H + 6, panelW - 40, 16)
                .tooltip(Tooltip.create(WikiLang.tr("phoenix_wiki.suite_hud_settings_screen.drops_every_hud_button_wherever")))
                .build());

        this.addRenderableWidget(Button.builder(WikiLang.tr("phoenix_wiki.suite_hud_settings_screen.reset_positions"), b -> {
                    SuiteHudBar.resetAllButtonPositions();
                    this.init();
                })
                .bounds(panelX + 20, panelY + panelH - FOOTER_H + 28, 110, 16)
                .tooltip(Tooltip.create(WikiLang.tr("phoenix_wiki.suite_hud_settings_screen.put_every_dragged_hud_button")))
                .build());

        this.addRenderableWidget(Button.builder(WikiLang.tr("phoenix_wiki.suite_hud_settings_screen.done"), b -> onClose())
                .bounds(panelX + panelW - 100, panelY + panelH - FOOTER_H + 28, 80, 16)
                .build());
    }

    private Button toggle(int x, int y, int w, String labelKey, boolean value, Consumer<Boolean> setter, String tooltipKey) {
        Component label = Component.literal(WikiText.str(labelKey) + ": "
                + (value ? "§aON" : WikiText.str("phoenix_wiki.suite_hud_settings.off")));
        return Button.builder(label, b -> {
                    setter.accept(!value);
                    this.init();
                })
                .bounds(x, y, w, 16)
                .tooltip(Tooltip.create(WikiLang.tr(tooltipKey)))
                .build();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var theme = PhoenixTheme.current();
        this.renderBackground(g);

        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1,
                theme.border.getColor());
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, theme.panel.getColor());

        g.drawCenteredString(this.font, WikiText.str("phoenix_wiki.suite_hud_settings_screen.suite_hud_settings"), panelX + panelW / 2, panelY + 8,
                theme.accent.getColor());

        int y = panelY + HEADER_H - ROW_H;
        g.drawString(this.font, WikiText.str("phoenix_wiki.suite_hud_settings.suite_wide_scale"), panelX + 12, y + 4, theme.text.getColor(), false);
        y += ROW_H + 4 + TOGGLE_ROWS * ROW_H;

        List<String> modIds = SuiteHudBar.getRegisteredModIds();
        if (modIds.isEmpty()) {
            g.drawString(this.font, WikiText.str("phoenix_wiki.suite_hud_settings.no_suite_mods_registered_yet"), panelX + 12, y + 4,
                    theme.textDim.getColor(), false);
        }
        for (String modId : visibleMods(modIds)) {
            Component tip = SuiteHudBar.getTooltip(modId);
            String name = tip != null ? tip.getString().replaceAll("§.", "") : modId;
            name = fit(name, toggleX() - panelX - 16);
            g.drawString(this.font, name, panelX + 12, y + 4, theme.text.getColor(), false);
            y += ROW_H;
        }

        if (rowCount > visibleRows) {
            int trackTop = panelY + HEADER_H + 4 + TOGGLE_ROWS * ROW_H - ROW_H + ROW_H;
            int trackH = visibleRows * ROW_H;
            int trackX = panelX + panelW - 6;
            g.fill(trackX, trackTop, trackX + 2, trackTop + trackH, theme.border.getColor());
            int thumbH = Math.max(8, trackH * visibleRows / rowCount);
            int thumbY = trackTop + (trackH - thumbH) * scrollRow / Math.max(1, rowCount - visibleRows);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, theme.accent.getColor());
        }

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollBy(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int toggleX() {
        return panelX + panelW - 178;
    }

    private List<String> visibleMods(List<String> modIds) {
        int from = Math.min(scrollRow, modIds.size());
        int to = Math.min(modIds.size(), from + visibleRows);
        return modIds.subList(from, to);
    }

    private String fit(String text, int maxW) {
        if (this.font.width(text) <= maxW) return text;
        int ellipsisW = this.font.width("…");
        return this.font.plainSubstrByWidth(text, Math.max(0, maxW - ellipsisW)) + "…";
    }

    private boolean scrollBy(double mouseX, double mouseY, double amount) {
        if (rowCount <= visibleRows || amount == 0) return false;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) return false;
        int next = Math.max(0, Math.min(rowCount - visibleRows, scrollRow - (int) Math.signum(amount)));
        if (next == scrollRow) return true;
        scrollRow = next;
        this.init();
        return true;
    }

    private static class ScaleSlider extends AbstractSliderButton {

        private final Consumer<Float> onChange;

        ScaleSlider(int x, int y, int w, int h, float initial, Consumer<Float> onChange) {
            super(x, y, w, h, Component.empty(), toFraction(initial));
            this.onChange = onChange;
            updateMessage();
        }

        private static double toFraction(float scale) {
            float clamped = Math.max(SuiteHudConfig.MIN_SCALE, Math.min(SuiteHudConfig.MAX_SCALE, scale));
            return (clamped - SuiteHudConfig.MIN_SCALE) / (SuiteHudConfig.MAX_SCALE - SuiteHudConfig.MIN_SCALE);
        }

        private float scale() {
            return (float) (SuiteHudConfig.MIN_SCALE +
                    value * (SuiteHudConfig.MAX_SCALE - SuiteHudConfig.MIN_SCALE));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("%.2fx", scale())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(scale());
        }
    }
}

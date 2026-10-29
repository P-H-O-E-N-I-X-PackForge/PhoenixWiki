package net.phoenixvine.wiki.client.suite;

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
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private final Screen parent;

    private static final int ROW_H = 20;
    private static final int PANEL_W = 300;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 50;

    private int panelX, panelY, panelW, panelH;

    public SuiteHudSettingsScreen(Screen parent) {
        super(Component.literal("Suite HUD Settings"));
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
        panelW = PANEL_W;
        panelH = HEADER_H + Math.max(1, modIds.size()) * ROW_H + FOOTER_H;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);

        int y = panelY + HEADER_H - ROW_H;

        this.addRenderableWidget(new ScaleSlider(panelX + 150, y, 130, 16,
                SuiteHudConfig.getGlobalScale(), SuiteHudConfig::setGlobalScale));
        y += ROW_H + 4;

        if (modIds.isEmpty()) {
            y += ROW_H;
        }

        for (String modId : modIds) {
            Component tip = SuiteHudBar.getTooltip(modId);
            String name = tip != null ? tip.getString().replaceAll("§.", "") : modId;
            if (name.length() > 14) name = name.substring(0, 13) + "…";

            final int rowY = y;
            boolean enabled = SuiteHudBar.isButtonEnabled(modId);
            this.addRenderableWidget(Button.builder(
                    Component.literal(enabled ? "§aON" : "§8OFF"),
                    b -> {
                        SuiteHudBar.setButtonEnabled(modId, !SuiteHudBar.isButtonEnabled(modId));
                        this.init();
                    })
                    .bounds(panelX + 122, rowY, 34, 16)
                    .tooltip(Tooltip.create(Component.literal("Show/hide this button in the HUD bar")))
                    .build());

            Float override = SuiteHudConfig.getButtonScaleOverride(modId);
            float sliderValue = override != null ? override : SuiteHudConfig.getGlobalScale();
            this.addRenderableWidget(new ScaleSlider(panelX + 160, rowY, 100, 16, sliderValue,
                    v -> SuiteHudConfig.setButtonScale(modId, v)));

            this.addRenderableWidget(Button.builder(Component.literal("↻"), b -> {
                        SuiteHudConfig.setButtonScale(modId, null);
                        this.init();
                    })
                    .bounds(panelX + 264, rowY, 16, 16)
                    .tooltip(Tooltip.create(Component.literal(override != null ?
                            "Reset to the suite-wide scale" : "Already following the suite-wide scale")))
                    .build());

            y += ROW_H;
        }

        this.addRenderableWidget(Button.builder(Component.literal(SuiteHudBar.isGravityMode() ?
                        "§a🪂 Gravity mode: ON" : "§8🪂 Gravity mode: OFF"),
                        b -> {
                            SuiteHudBar.setGravityMode(!SuiteHudBar.isGravityMode());
                            this.init();
                        })
                .bounds(panelX + 20, panelY + panelH - FOOTER_H + 6, panelW - 40, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Drops every HUD button -- wherever it's currently sitting -- to the\n" +
                                "bottom of the screen, piling up like dropped items. Turning it off\n" +
                                "puts dropped buttons back in the grid.")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("§7Reset positions"), b -> {
                    SuiteHudBar.resetAllButtonPositions();
                    this.init();
                })
                .bounds(panelX + 20, panelY + panelH - FOOTER_H + 28, 110, 16)
                .tooltip(Tooltip.create(Component.literal("Put every dragged HUD button back in its default spot")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + panelW - 100, panelY + panelH - FOOTER_H + 28, 80, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var theme = PhoenixTheme.current();
        this.renderBackground(g, mouseX, mouseY, partial);

        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1,
                theme.border.getColor());
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, theme.panel.getColor());

        g.drawCenteredString(this.font, "Suite HUD Settings", panelX + panelW / 2, panelY + 8,
                theme.accent.getColor());

        int y = panelY + HEADER_H - ROW_H;
        g.drawString(this.font, "Suite-wide scale", panelX + 12, y + 4, theme.text.getColor(), false);
        y += ROW_H + 4;

        List<String> modIds = SuiteHudBar.getRegisteredModIds();
        if (modIds.isEmpty()) {
            g.drawString(this.font, "§7No suite mods registered yet.", panelX + 12, y + 4,
                    theme.textDim.getColor(), false);
        }
        for (String modId : modIds) {
            Component tip = SuiteHudBar.getTooltip(modId);
            String name = tip != null ? tip.getString().replaceAll("§.", "") : modId;
            if (name.length() > 14) name = name.substring(0, 13) + "…";
            g.drawString(this.font, name, panelX + 12, y + 4, theme.text.getColor(), false);
            y += ROW_H;
        }

        super.render(g, mouseX, mouseY, partial);
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

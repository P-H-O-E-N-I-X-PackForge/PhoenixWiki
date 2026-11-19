package net.phoenixvine.wiki.ui.hud;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.ModularPanel;

public final class HudPanel {

    private HudPanel() {}

    public static final IDrawable BACKGROUND = HudTheme.TEAL.background();

    public static void apply(ModularPanel<?> panel, TabStrip screen) {
        HudTheme theme = screen.theme();
        panel.background(theme.background());
        var inner = screen.stripPainter();
        // a tab pressed while the machine UI is on its sides or pages page takes it back to its main page
        var strip = new CanvasWidget(new CanvasPainter() {

            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int w, int h, int mouseX, int mouseY) {
                inner.render(g, w, h, mouseX, mouseY);
            }

            @Override
            public boolean click(double mouseX, double mouseY, int button) {
                boolean used = inner.click(mouseX, mouseY, button);
                // a press on the strip, even on the tab that is open already
                if (button == 0) {
                    // by name, as only the machine UI panels of GregTech Firebird have it
                    try {
                        panel.getClass().getMethod("showMainPage").invoke(panel);
                    } catch (ReflectiveOperationException ignored) {}
                }
                return used;
            }

            @Override
            public void drag(double mouseX, double mouseY, int button) {
                inner.drag(mouseX, mouseY, button);
            }

            @Override
            public void release(int button) {
                inner.release(button);
            }

            @Override
            public boolean scroll(double mouseX, double mouseY, double amount) {
                return inner.scroll(mouseX, mouseY, amount);
            }
        });
        strip.size(screen.stripWidth(), screen.stripHeight());
        if (screen.horizontal()) strip.leftRel(0f, 8, 0f).topRel(0f, 0, 1f);
        // on the left, with the machine UI's other side columns, not on the right
        else strip.rightRel(1f).top(8);
        strip.excludeAreaInRecipeViewer();
        panel.child(strip);
    }
}

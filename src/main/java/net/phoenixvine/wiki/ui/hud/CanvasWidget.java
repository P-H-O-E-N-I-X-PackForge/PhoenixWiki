package net.phoenixvine.wiki.ui.hud;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;

@OnlyIn(Dist.CLIENT)
public class CanvasWidget extends Widget<CanvasWidget> implements Interactable {

    private final CanvasPainter painter;
    private int fitWidth = 0;
    private int fitHeight = 0;

    public CanvasWidget(CanvasPainter painter) {
        this.painter = painter;
    }

    public CanvasWidget fittedTo(int panelWidth, int panelHeight) {
        this.fitWidth = panelWidth;
        this.fitHeight = panelHeight;
        return this;
    }

    // the mouse in this widget's own pixels, as the drawing saw it: ModularUI takes the panel's scale into account in
    // it. The events use it too, as the panel is scaled to fit the window and the absolute mouse is not
    private int lastX;
    private int lastY;

    private int localX(ModularGuiContext ctx) {
        return lastX;
    }

    private int localY(ModularGuiContext ctx) {
        return lastY;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        lastX = context.getMouseX();
        lastY = context.getMouseY();
        var g = context.getGraphics();
        GuiDraw.enableScissor(g, 0, 0, getArea().width, getArea().height);
        painter.render(g, getArea().width, getArea().height, localX(context), localY(context));
        g.disableScissor();
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        ModularGuiContext ctx = getContext();
        boolean used = painter.click(localX(ctx), localY(ctx), mouseButton);
        net.phoenixvine.wiki.PhoenixWiki.LOGGER.debug("[HUD] press {} at {},{} in {}x{}: {}", mouseButton, localX(ctx),
                localY(ctx), getArea().width, getArea().height, used ? "used" : "ignored");
        return used ? Result.SUCCESS : Result.IGNORE;
    }

    @Override
    public void onMouseDrag(int mouseButton, double dragX, double dragY) {
        ModularGuiContext ctx = getContext();
        painter.drag(localX(ctx), localY(ctx), mouseButton);
    }

    @Override
    public boolean onMouseReleased(int mouseButton) {
        painter.release(mouseButton);
        return false;
    }

    @Override
    public boolean onMouseScrolled(double scrollX, double scrollY) {
        ModularGuiContext ctx = getContext();
        return painter.scroll(localX(ctx), localY(ctx), scrollY);
    }
}

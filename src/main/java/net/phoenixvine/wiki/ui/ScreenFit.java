package net.phoenixvine.wiki.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

/**
 * Lets a full-screen UI be composed for a 480 by 270 stage and scaled down when the window (or a high GUI scale) leaves
 * less room than that. A screen calls {@link #apply} at the start of its render, scales the pose by the returned factor,
 * and divides mouse positions by it. The screen's own width and height are left at the stage size, so the layout code and
 * the mouse handlers between frames all agree.
 */
public final class ScreenFit {

    private ScreenFit() {}

    public static final float STAGE_W = 480f;
    public static final float STAGE_H = 270f;
    private static final float MIN = 0.5f;

    /** The scale for a window of this many GUI pixels: 1 when it is big enough, down to 0.5 when it is small. */
    public static float factor(int guiWidth, int guiHeight) {
        return Mth.clamp(Math.min(guiWidth / STAGE_W, guiHeight / STAGE_H), MIN, 1f);
    }

    /** Set the screen's size to the stage it is laid out on, and return the factor to scale the drawing by. */
    public static float apply(Screen screen) {
        var window = Minecraft.getInstance().getWindow();
        int realW = window.getGuiScaledWidth();
        int realH = window.getGuiScaledHeight();
        float k = factor(realW, realH);
        screen.width = (int) Math.ceil(realW / k);
        screen.height = (int) Math.ceil(realH / k);
        return k;
    }
}

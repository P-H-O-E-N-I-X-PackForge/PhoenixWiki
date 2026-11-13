package net.phoenixvine.wiki.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

import java.util.Random;

/** Immediate-mode 2D shapes for GUI effects: discs, rings, rays, bolts. One copy for every screen in the suite. */
public final class GlDraw {

    private GlDraw() {}

    public static int argb(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float noise(float a, float b) {
        double v = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    public static BufferBuilder begin(boolean additive) {
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void end(BufferBuilder buf, UiProfiler prof) {
        MeshData mesh = buf.build();
        if (mesh != null) {
            if (prof != null) {
                prof.count("vertices", mesh.drawState().vertexCount());
                prof.count("draws", 1);
            }
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    public static void quad(BufferBuilder buf, Matrix4f pose, float x, float y, float s, int color) {
        buf.addVertex(pose, x - s, y - s, 0).setColor(color);
        buf.addVertex(pose, x - s, y + s, 0).setColor(color);
        buf.addVertex(pose, x + s, y + s, 0).setColor(color);
        buf.addVertex(pose, x + s, y - s, 0).setColor(color);
    }

    public static void line(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1, float w0, float w1,
                        int c0, int c1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float nx = -dy / len;
        float ny = dx / len;
        buf.addVertex(pose, x0 + nx * w0, y0 + ny * w0, 0).setColor(c0);
        buf.addVertex(pose, x0 - nx * w0, y0 - ny * w0, 0).setColor(c0);
        buf.addVertex(pose, x1 - nx * w1, y1 - ny * w1, 0).setColor(c1);
        buf.addVertex(pose, x1 + nx * w1, y1 + ny * w1, 0).setColor(c1);
    }

    public static void line(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1, float w0, float w1,
                            int c) {
        line(buf, pose, x0, y0, x1, y1, w0, w1, c, c);
    }

    public static void line(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1, float w, int c) {
        line(buf, pose, x0, y0, x1, y1, w, w, c, c);
    }

    public static void ring(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r, float thick, float start,
                            float sweep, int color) {
        ring(buf, pose, cx, cy, r, thick, start, sweep, color, 16);
    }

    public static void ring(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r, float thick, float start,
                            float sweep, int color, int minSegments) {
        int segments = Math.max(minSegments, (int) (r / 3f));
        for (int i = 0; i < segments; i++) {
            float a0 = start + sweep * i / segments;
            float a1 = start + sweep * (i + 1) / segments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            buf.addVertex(pose, cx + c0 * (r - thick), cy + s0 * (r - thick), 0).setColor(color);
            buf.addVertex(pose, cx + c0 * (r + thick), cy + s0 * (r + thick), 0).setColor(color);
            buf.addVertex(pose, cx + c1 * (r + thick), cy + s1 * (r + thick), 0).setColor(color);
            buf.addVertex(pose, cx + c1 * (r - thick), cy + s1 * (r - thick), 0).setColor(color);
        }
    }

    public static void disc(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r, int inner, int outer) {
        int segments = Math.max(16, (int) (r / 2.5f));
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float x0 = cx + (float) Math.cos(a0) * r;
            float y0 = cy + (float) Math.sin(a0) * r;
            float x1 = cx + (float) Math.cos(a1) * r;
            float y1 = cy + (float) Math.sin(a1) * r;
            buf.addVertex(pose, cx, cy, 0).setColor(inner);
            buf.addVertex(pose, x0, y0, 0).setColor(outer);
            buf.addVertex(pose, x1, y1, 0).setColor(outer);
            buf.addVertex(pose, x1, y1, 0).setColor(outer);
        }
    }

    public static void tri(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1, float x2, float y2,
                       int c0, int c1, int c2) {
        buf.addVertex(pose, x0, y0, 0).setColor(c0);
        buf.addVertex(pose, x1, y1, 0).setColor(c1);
        buf.addVertex(pose, x2, y2, 0).setColor(c2);
        buf.addVertex(pose, x2, y2, 0).setColor(c2);
    }

    public static void polygon(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r, int sides, float rot,
                           float thick, int color) {
        for (int i = 0; i < sides; i++) {
            float a0 = rot + Mth.TWO_PI * i / sides;
            float a1 = rot + Mth.TWO_PI * (i + 1) / sides;
            line(buf, pose, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, thick, color);
        }
    }

    public static void polyFill(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r, int sides, float rot,
                            int inner, int outer) {
        for (int i = 0; i < sides; i++) {
            float a0 = rot + Mth.TWO_PI * i / sides;
            float a1 = rot + Mth.TWO_PI * (i + 1) / sides;
            tri(buf, pose, cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, inner, outer, outer);
        }
    }

    public static void rays(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r0, float r1, int count,
                        float rot, float halfWidth, int color, float alpha) {
        for (int i = 0; i < count; i++) {
            float a = rot + Mth.TWO_PI * i / count;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float nx = -sin * halfWidth;
            float ny = cos * halfWidth;
            float x0 = cx + cos * r0;
            float y0 = cy + sin * r0;
            float x1 = cx + cos * r1;
            float y1 = cy + sin * r1;
            buf.addVertex(pose, x0 + nx * 0.2f, y0 + ny * 0.2f, 0).setColor(argb(color, alpha));
            buf.addVertex(pose, x0 - nx * 0.2f, y0 - ny * 0.2f, 0).setColor(argb(color, alpha));
            buf.addVertex(pose, x1 - nx, y1 - ny, 0).setColor(argb(color, 0f));
            buf.addVertex(pose, x1 + nx, y1 + ny, 0).setColor(argb(color, 0f));
        }
    }

    public static void ticks(BufferBuilder buf, Matrix4f pose, float cx, float cy, float r, int count, int major,
                         float rot, float len, int color) {
        for (int i = 0; i < count; i++) {
            float a = rot + Mth.TWO_PI * i / count;
            float l = i % major == 0 ? len * 2f : len;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            line(buf, pose, cx + cos * r, cy + sin * r, cx + cos * (r + l), cy + sin * (r + l),
                    i % major == 0 ? 0.9f : 0.5f, color);
        }
    }

    public static void bolt(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1, float jitter,
                        long seed, int color, float alpha) {
        Random r = new Random(seed);
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 3f) return;
        int segments = Math.max(4, (int) (len / 11f));
        float nx = -dy / len;
        float ny = dx / len;
        float px = x0;
        float py = y0;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float off = i == segments ? 0 : (r.nextFloat() - 0.5f) * 2f * jitter * (float) Math.sin(Math.PI * t);
            float x = x0 + dx * t + nx * off;
            float y = y0 + dy * t + ny * off;
            line(buf, pose, px, py, x, y, 2.2f, argb(color, alpha * 0.2f));
            line(buf, pose, px, py, x, y, 0.6f, argb(0xFFFFFF, alpha));
            px = x;
            py = y;
        }
    }
}

package wtf.moonlight.ui.imgui;

import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.ui.clickgui.Colors;
import wtf.moonlight.ui.clickgui.Controls;
import wtf.moonlight.ui.esp.Kind;
import wtf.moonlight.ui.esp.Models;
import wtf.moonlight.ui.esp.VanillaTag;
import wtf.moonlight.ui.esp.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PreviewModels implements Models {
    private static final Identifier ZOMBIE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final Identifier SWORD = Identifier.withDefaultNamespace("textures/item/diamond_sword.png");
    private static final Identifier TNT_SIDE = Identifier.withDefaultNamespace("textures/block/tnt_side.png");
    private static final Identifier TNT_TOP = Identifier.withDefaultNamespace("textures/block/tnt_top.png");
    private static final Identifier TNT_BOTTOM = Identifier.withDefaultNamespace("textures/block/tnt_bottom.png");
    private static final Identifier CRYSTAL = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final Identifier FIRE = Identifier.withDefaultNamespace("textures/item/fire_charge.png");
    private static final int TAG_BACKGROUND = Colors.rgba(0, 0, 0, 64);
    private static final int WHITE = Colors.rgb(255, 255, 255);
    private static final float PIXEL = 1 / 16F;
    private static final float PLAYER_SCALE = .9375F;
    private static final float MODEL_PIVOT = 1.501F;
    private static final float TICKS = 20;
    private static final float ARMS = (float) Math.toRadians(-80);
    private static final float SPREAD = .1F;
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4);
    private static final float ITEM_DEPTH = 1 / 32F;
    private static final float FIRE_DEPTH = 3 / 32F;
    private static final int MAX_SLICES = 16;
    private static final float[] FULL = {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1};
    private static final List<Part> PLAYER_PARTS = List.of(
            new Part(0, 0, 0, 0, 0, -4, -8, -4, 8, 8, 8, 0, 0, 0, false),
            new Part(0, 0, 0, 0, 0, -4, -8, -4, 8, 8, 8, 32, 0, .5F, false),
            new Part(0, 0, 0, 0, 0, -4, 0, -2, 8, 12, 4, 16, 16, 0, false),
            new Part(0, 0, 0, 0, 0, -4, 0, -2, 8, 12, 4, 16, 32, .25F, false),
            new Part(-5, 2, 0, 0, 0, -3, -2, -2, 4, 12, 4, 40, 16, 0, false),
            new Part(-5, 2, 0, 0, 0, -3, -2, -2, 4, 12, 4, 40, 32, .25F, false),
            new Part(5, 2, 0, 0, 0, -1, -2, -2, 4, 12, 4, 32, 48, 0, false),
            new Part(5, 2, 0, 0, 0, -1, -2, -2, 4, 12, 4, 48, 48, .25F, false),
            new Part(-1.9F, 12, 0, 0, 0, -2, 0, -2, 4, 12, 4, 0, 16, 0, false),
            new Part(-1.9F, 12, 0, 0, 0, -2, 0, -2, 4, 12, 4, 0, 32, .25F, false),
            new Part(1.9F, 12, 0, 0, 0, -2, 0, -2, 4, 12, 4, 16, 48, 0, false),
            new Part(1.9F, 12, 0, 0, 0, -2, 0, -2, 4, 12, 4, 0, 48, .25F, false));
    private static final List<Part> ZOMBIE_PARTS = List.of(
            new Part(0, 0, 0, 0, 0, -4, -8, -4, 8, 8, 8, 0, 0, 0, false),
            new Part(0, 0, 0, 0, 0, -4, -8, -4, 8, 8, 8, 32, 0, .5F, false),
            new Part(0, 0, 0, 0, 0, -4, 0, -2, 8, 12, 4, 16, 16, 0, false),
            new Part(-5, 2, 0, ARMS, -SPREAD, -3, -2, -2, 4, 12, 4, 40, 16, 0, false),
            new Part(5, 2, 0, ARMS, SPREAD, -1, -2, -2, 4, 12, 4, 40, 16, 0, true),
            new Part(-1.9F, 12, 0, 0, 0, -2, 0, -2, 4, 12, 4, 0, 16, 0, false),
            new Part(1.9F, 12, 0, 0, 0, -2, 0, -2, 4, 12, 4, 0, 16, 0, true));

    private final Identifier skin;
    private final List<Face> faces = new ArrayList<>();
    private final float[] screenX = new float[8];
    private final float[] screenY = new float[8];
    private final float[] depth = new float[8];
    private final Vector3f point = new Vector3f();
    private View view = new View(Kind.PLAYER, 0, 0);
    private float x;
    private float y;
    private float scale;

    PreviewModels(Identifier skin) {
        this.skin = skin;
    }

    @Override
    public @Nullable String name(Kind kind) {
        return null;
    }

    @Override
    public void draw(ImDrawList draw, Kind kind, View view, @Nullable String tag, float left, float top, float right, float bottom, float scale) {
        this.view = view;
        this.x = (left + right) / 2;
        this.y = (top + bottom) / 2;
        this.scale = scale;
        float ticks = (float) ImGui.getTime() * TICKS;
        faces.clear();
        switch (kind) {
            case PLAYER -> humanoid(Textures.of(skin), PLAYER_PARTS, PLAYER_SCALE);
            case MOB -> humanoid(Textures.of(ZOMBIE), ZOMBIE_PARTS, 1);
            case ITEM -> {
                float bob = (float) Math.sin(ticks / 10) * .1F + .1F;
                sprite(Textures.of(SWORD), new Matrix4f().translate(0, bob + .3125F, 0).rotateY(ticks / 20), .25F, ITEM_DEPTH);
            }
            case TNT -> {
                long side = Textures.of(TNT_SIDE);
                box(new Matrix4f().scale(-PIXEL, -PIXEL, PIXEL), -8, -16, -8, 16, 16, 16, 0, false,
                        new long[]{Textures.of(TNT_TOP), Textures.of(TNT_BOTTOM), side, side, side, side}, FULL);
            }
            case CRYSTAL -> crystal(Textures.of(CRYSTAL), ticks);
            case FIREBALL -> sprite(Textures.of(FIRE), new Matrix4f().translate(0, .375F, 0), .75F, FIRE_DEPTH);
        }
        faces.sort(Comparator.comparingDouble(Face::depth).reversed());
        for (Face face : faces) {
            float[] fx = face.x();
            float[] fy = face.y();
            float[] fu = face.u();
            float[] fv = face.v();
            draw.addImageQuad(face.texture(), fx[0], fy[0], fx[1], fy[1], fx[2], fy[2], fx[3], fy[3], fu[0], fv[0], fu[1], fv[1], fu[2], fv[2], fu[3], fv[3]);
        }
        if (tag != null) tag(draw, kind, tag);
    }

    private void tag(ImDrawList draw, Kind kind, String text) {
        float[] rect = VanillaTag.bounds(kind, view, text, x, y, scale);
        float size = VanillaTag.size(scale);
        float width = Fonts.regular().calcTextSizeAX(size, Float.MAX_VALUE, 0, text);
        draw.addRectFilled(rect[0], rect[1], rect[2], rect[3], TAG_BACKGROUND);
        Controls.addText(draw, Fonts.regular(), size, (rect[0] + rect[2] - width) / 2, rect[3] - size, WHITE, text);
    }

    private void humanoid(long texture, List<Part> parts, float size) {
        long[] textures = {texture, texture, texture, texture, texture, texture};
        Matrix4f base = new Matrix4f().scale(-size, -size, size).translate(0, -MODEL_PIVOT, 0);
        for (Part part : parts) {
            Matrix4f pose = new Matrix4f(base).translate(part.pivotX() * PIXEL, part.pivotY() * PIXEL, part.pivotZ() * PIXEL)
                    .rotateZYX(0, part.yRot(), part.xRot()).scale(PIXEL);
            box(pose, part.x(), part.y(), part.z(), part.width(), part.height(), part.depth(), part.grow(), part.mirror(), textures,
                    layout(part.u(), part.v(), part.width(), part.height(), part.depth(), 64, 64));
        }
    }

    private void crystal(long texture, float ticks) {
        long[] textures = {texture, texture, texture, texture, texture, texture};
        Matrix4f root = new Matrix4f().scale(2).translate(0, -.5F, 0);
        box(new Matrix4f(root).scale(PIXEL), -6, 0, -6, 12, 4, 12, 0, false, textures, layout(0, 16, 12, 4, 12, 64, 32));
        float bob = (float) Math.sin(ticks * .2F) / 2 + .5F;
        float crystalY = (bob * bob + bob) * .4F - 1.4F;
        float spin = (float) Math.toRadians(ticks * 3);
        Quaternionf turn = new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45).rotateY(spin);
        Matrix4f outer = new Matrix4f(root).translate(0, (24 + 8 * crystalY) * PIXEL, 0)
                .rotate(new Quaternionf().rotationY(spin).rotateAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45));
        float[] glass = layout(0, 0, 8, 8, 8, 64, 32);
        box(new Matrix4f(outer).scale(PIXEL), -4, -4, -4, 8, 8, 8, 0, false, textures, glass);
        Matrix4f inner = new Matrix4f(outer).rotate(turn).scale(.875F);
        box(new Matrix4f(inner).scale(PIXEL), -4, -4, -4, 8, 8, 8, 0, false, textures, glass);
        Matrix4f core = new Matrix4f(inner).rotate(turn).scale(.765625F);
        box(core.scale(PIXEL), -4, -4, -4, 8, 8, 8, 0, false, textures, layout(32, 0, 8, 8, 8, 64, 32));
    }

    private void sprite(long texture, Matrix4f pose, float half, float thickness) {
        int slices = Math.clamp(Math.round(thickness * scale) + 1, 2, MAX_SLICES);
        float[] cornerX = {half, -half, -half, half};
        float[] cornerY = {half, half, -half, -half};
        float[] us = {0, 1, 1, 0};
        float[] vs = {0, 0, 1, 1};
        for (int slice = 0; slice < slices; slice++) {
            float z = thickness * ((float) slice / (slices - 1) - .5F);
            for (int i = 0; i < 4; i++) project(pose, cornerX[i], cornerY[i], z, i);
            add(texture, new int[]{0, 1, 2, 3}, us, vs);
        }
    }

    private static float[] layout(int u, int v, int width, int height, int depth, float textureWidth, float textureHeight) {
        int u1 = u + depth;
        int u2 = u1 + width;
        int u3 = u2 + depth;
        int v1 = v + depth;
        int v2 = v1 + height;
        float[] pixels = {u1, v, u2, v1, u2, v1, u2 + width, v, u, v1, u1, v2, u1, v1, u2, v2, u2, v1, u3, v2, u3, v1, u3 + width, v2};
        for (int i = 0; i < pixels.length; i++) pixels[i] /= i % 2 == 0 ? textureWidth : textureHeight;
        return pixels;
    }

    private void box(Matrix4f pose, float x0, float y0, float z0, float width, float height, float boxDepth, float grow, boolean mirror,
                     long[] textures, float[] uv) {
        float minX = x0 - grow;
        float maxX = x0 + width + grow;
        if (mirror) {
            float swap = minX;
            minX = maxX;
            maxX = swap;
        }
        for (int corner = 0; corner < 8; corner++) {
            project(pose, (corner & 1) == 0 ? minX : maxX, (corner & 2) == 0 ? y0 - grow : y0 + height + grow,
                    (corner & 4) == 0 ? z0 - grow : z0 + boxDepth + grow, corner);
        }
        int[][] orders = {{5, 4, 0, 1}, {3, 2, 6, 7}, {0, 4, 6, 2}, {1, 0, 2, 3}, {5, 1, 3, 7}, {4, 5, 7, 6}};
        for (int face = 0; face < 6; face++) {
            int[] order = orders[face];
            float u0 = uv[face * 4];
            float v0 = uv[face * 4 + 1];
            float u1 = uv[face * 4 + 2];
            float v1 = uv[face * 4 + 3];
            float[] us = {u1, u0, u0, u1};
            float[] vs = {v0, v0, v1, v1};
            if (mirror) {
                order = new int[]{order[3], order[2], order[1], order[0]};
                vs = new float[]{v1, v1, v0, v0};
            }
            float area = 0;
            for (int i = 0; i < 4; i++) {
                int from = order[i];
                int to = order[(i + 1) % 4];
                area += screenX[from] * screenY[to] - screenX[to] * screenY[from];
            }
            if (area < 0) add(textures[face], order, us, vs);
        }
    }

    private void project(Matrix4f pose, float px, float py, float pz, int index) {
        pose.transformPosition(point.set(px, py, pz));
        screenX[index] = x + view.x(point.x, point.z) * scale;
        screenY[index] = y + view.y(point.x, point.y, point.z) * scale;
        depth[index] = view.depth(point.x, point.y, point.z);
    }

    private void add(long texture, int[] order, float[] us, float[] vs) {
        float[] faceX = new float[4];
        float[] faceY = new float[4];
        float total = 0;
        for (int i = 0; i < 4; i++) {
            faceX[i] = screenX[order[i]];
            faceY[i] = screenY[order[i]];
            total += depth[order[i]];
        }
        faces.add(new Face(texture, faceX, faceY, us, vs, total / 4));
    }

    private record Part(float pivotX, float pivotY, float pivotZ, float xRot, float yRot, float x, float y, float z,
                        int width, int height, int depth, int u, int v, float grow, boolean mirror) {
    }

    private record Face(long texture, float[] x, float[] y, float[] u, float[] v, float depth) {
    }
}

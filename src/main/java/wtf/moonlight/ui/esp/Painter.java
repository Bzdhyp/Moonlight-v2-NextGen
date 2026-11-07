package wtf.moonlight.ui.esp;

import imgui.ImDrawList;
import imgui.ImFont;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.module.impl.render.Esp;
import wtf.moonlight.ui.clickgui.Colors;
import wtf.moonlight.ui.clickgui.Controls;
import wtf.moonlight.ui.imgui.Effects;
import wtf.moonlight.ui.imgui.Fonts;
import wtf.moonlight.ui.imgui.Natives;
import wtf.moonlight.value.impl.ColorValue;

public final class Painter {
    private static final int BLACK = Colors.rgb(0, 0, 0);
    private static final int WHITE = Colors.rgb(255, 255, 255);
    private static final int SHADE = Colors.rgba(0, 0, 0, 180);
    private static final int STRENGTH_GLOW = Colors.rgba(255, 30, 30, 235);
    private static final int STRENGTH_TINT = Colors.rgb(255, 40, 40);
    private static final int GRAY = Colors.rgb(170, 170, 170);
    private static final int DARK_RED = Colors.rgb(170, 0, 0);
    private static final float ROUND = 5;
    private static final float NAME = 8.5F;
    private static final float HEALTH = 7;
    private static final float CLASSIC = 8;
    private static final float TEXT_SCALE = .85F;
    private static final float STRENGTH_MIX = .35F;
    private static final float HEART_SCALE = .8F;
    private static final String HEART = Character.toString(0xF004);
    private static final String OPEN = " [";
    private static final String CLOSE = "]";

    private final Esp esp;
    private float u = 1;
    private float @Nullable [] healthPlace;
    private float @Nullable [] armorPlace;

    public Painter(Esp esp) {
        this.esp = esp;
    }

    public void paint(ImDrawList draw, Target target, float unit, Pass pass) {
        u = unit;
        if (esp.elements().has(Esp.BOX)) box(draw, target, pass);
        if (showsHealth(target)) health(draw, target, rect(target, unit, true), pass);
        if (showsArmor(target)) armor(draw, target, rect(target, unit, false), pass);
        if (esp.elements().has(Esp.NAME_TAG) && !esp.vanillaNames()) name(draw, target, pass);
    }

    public void place(float @Nullable [] health, float @Nullable [] armor) {
        healthPlace = health;
        armorPlace = armor;
    }

    public boolean showsHealth(Target t) {
        return t.living() && esp.elements().has(Esp.HEALTH_BAR);
    }

    public boolean showsArmor(Target t) {
        return t.living() && t.armor() > 0 && esp.elements().has(Esp.ARMOR_BAR);
    }

    public float[] track(Target t, float unit, boolean health) {
        int side = (health ? esp.healthSide() : esp.armorSide()).get();
        float out = !health && showsHealth(t) && esp.healthSide().get() == side ? 2 * unit : 0;
        float x1 = t.minX();
        float y1 = t.minY();
        float x2 = t.maxX();
        float y2 = t.maxY();
        return switch (side) {
            case Esp.LEFT -> new float[]{x1 - 2.5F * unit - out, y1 - unit / 2, x1 - unit - out, y2 + unit};
            case Esp.RIGHT -> new float[]{x2 + 1.5F * unit + out, y1 - unit / 2, x2 + 3 * unit + out, y2 + unit};
            case Esp.TOP -> new float[]{x1 - unit / 2, y1 - 2.5F * unit - out, x2 + unit, y1 - unit - out};
            default -> new float[]{x1 - unit / 2, y2 + 1.5F * unit + out, x2 + unit, y2 + 3 * unit + out};
        };
    }

    private float lift(Target t, float unit) {
        float base = t.minY() - unit / 2;
        float lifted = 0;
        if (showsHealth(t)) lifted = Math.max(lifted, base - rect(t, unit, true)[1]);
        if (showsArmor(t)) lifted = Math.max(lifted, base - rect(t, unit, false)[1]);
        return lifted;
    }

    private float[] rect(Target t, float unit, boolean health) {
        float[] placed = health ? healthPlace : armorPlace;
        return placed != null ? placed : track(t, unit, health);
    }

    private void box(ImDrawList draw, Target t, Pass pass) {
        float x1 = t.minX();
        float y1 = t.minY();
        float x2 = t.maxX();
        float y2 = t.maxY();
        float w = x2 - x1;
        float h = y2 - y1;
        int first = accent(esp.boxColor(), esp.boxSecond(), 0);
        int second = accent(esp.boxColor(), esp.boxSecond(), 500);
        boolean corners = esp.boxMode().get() == 1;
        float line = u / 2;
        if (pass == Pass.SHADOW && esp.boxGlow().get()) {
            if (corners) {
                float fw = w / 4;
                float fh = h / 4;
                glow(draw, x1, y1 + line, line, fh, second);
                glow(draw, x1, y2 - fh, line, fh, second);
                glow(draw, x1, y1, fw, line, second);
                glow(draw, x2 - fw, y1, fw, line, second);
                glow(draw, x2, y1, line, fh + line, second);
                glow(draw, x2, y2 - fh, line, fh + line, second);
                glow(draw, x1, y2, fw, line, second);
                glow(draw, x2 - fw, y2, fw, line, second);
            } else {
                glow(draw, x1, y1 + line, line, h - line, first);
                glow(draw, x1, y1, w, line, first);
                glow(draw, x2, y1, line, h, first);
                glow(draw, x1, y2, w + line, line, first);
            }
        }
        if (pass != Pass.FILL) return;
        if (!corners) {
            rect(draw, x1 - line, y1 + u, 3 * line, h - 3 * line, BLACK);
            rect(draw, x1 - line, y1 - line, w + 3 * line, 3 * line, BLACK);
            rect(draw, x2 - line, y1 + u, 3 * line, h, BLACK);
            rect(draw, x1 - line, y2 - line, w, 3 * line, BLACK);
            across(draw, x1, y1 + line, line, h - line, first, second);
            across(draw, x1, y1, w, line, first, second);
            across(draw, x2, y1, line, h, first, second);
            across(draw, x1, y2, w + line, line, first, second);
            return;
        }
        float fw = w / 4;
        float fh = h / 4;
        rect(draw, x1 - line, y1 + u, 3 * line, fh, BLACK);
        rect(draw, x1 - line, y2 - fh - line, 3 * line, fh, BLACK);
        rect(draw, x1 - line, y1 - line, fw + u, 3 * line, BLACK);
        rect(draw, x2 - fw - line, y1 - line, fw + 3 * line, 3 * line, BLACK);
        rect(draw, x2 - line, y1 + u, 3 * line, fh, BLACK);
        rect(draw, x2 - line, y2 - fh - line, 3 * line, fh, BLACK);
        rect(draw, x1 - line, y2 - line, fw + u, 3 * line, BLACK);
        rect(draw, x2 - fw - line, y2 - line, fw + 3 * line, 3 * line, BLACK);
        rect(draw, x1, y1 + line, line, fh, second);
        rect(draw, x1, y2 - fh, line, fh, second);
        rect(draw, x1, y1, fw, line, second);
        rect(draw, x2 - fw, y1, fw, line, second);
        rect(draw, x2, y1, line, fh + line, second);
        rect(draw, x2, y2 - fh, line, fh + line, second);
        rect(draw, x1, y2, fw, line, second);
        rect(draw, x2 - fw, y2, fw, line, second);
    }

    private void health(ImDrawList draw, Target t, float[] track, Pass pass) {
        float ratio = t.health() / t.maxHealth();
        float filled = Math.clamp(ratio, 0, 1);
        int first = esp.healthColor().get();
        int second = esp.healthSecond().get();
        int mode = esp.healthMode().get();
        int color = switch (mode) {
            case 0 -> hue(ratio / 3);
            case 1 -> accent(esp.healthColor(), esp.healthSecond(), 0);
            default -> Colors.mix(second, first, filled);
        };
        bar(draw, track, filled, true, mode == 2 ? second : color, color, esp.healthGlow().get() ? color : 0, .75F * u, pass);
    }

    private void armor(ImDrawList draw, Target t, float[] track, Pass pass) {
        float ratio = t.armor() / 20.0F;
        int first = esp.armorColor().get();
        bar(draw, track, ratio, false, first, Colors.mix(esp.armorSecond().get(), first, ratio), esp.armorGlow().get() ? first : 0, 0, pass);
    }

    private void bar(ImDrawList draw, float[] track, float fill, boolean minimum, int start, int end, int glow, float spread, Pass pass) {
        boolean vertical = track[3] - track[1] >= track[2] - track[0];
        float minX = track[0] + u / 2;
        float minY = track[1] + u / 2;
        float maxX = track[2] - u / 2;
        float maxY = track[3] - u / 2;
        float length = Math.max(0, vertical ? maxY - minY : maxX - minX);
        float filled = minimum ? length - Math.max(0, length - 2.5F * u) * (1 - fill) : length * fill;
        if (vertical) minY = maxY - filled;
        else maxX = minX + filled;
        if (pass == Pass.SHADOW && glow != 0) {
            if (vertical) glow(draw, minX - spread, minY, maxX - minX + 2 * spread, maxY - minY, glow);
            else glow(draw, minX, minY - spread, maxX - minX, maxY - minY + 2 * spread, glow);
        }
        if (pass != Pass.FILL) return;
        draw.addRectFilled(track[0], track[1], track[2], track[3], SHADE);
        if (start == end) draw.addRectFilled(minX, minY, maxX, maxY, end);
        else if (vertical) down(draw, minX, minY, maxX - minX, maxY - minY, end, start);
        else across(draw, minX, minY, maxX - minX, maxY - minY, start, end);
    }

    private void name(ImDrawList draw, Target t, Pass pass) {
        if (esp.nameStyle().get() == 0) modern(draw, t, pass);
        else classic(draw, t, pass);
    }

    public float[] tag(Target t, float unit) {
        float lifted = lift(t, unit);
        float center = (t.minX() + t.maxX()) / 2;
        if (esp.nameStyle().get() == 0) {
            float nameWidth = width(Fonts.regular(), NAME * TEXT_SCALE * unit, t.name());
            float height = (NAME - 2 + (esp.nameHealth().get() && t.living() ? HEALTH : 0) + 4) * unit;
            float top = t.minY() - lifted - 2 * unit - height + unit;
            float left = center - 2 * unit - nameWidth / 2;
            return new float[]{left, top, left + nameWidth + 4 * unit, top + height};
        }
        float total = classicWidth(t, CLASSIC * TEXT_SCALE * unit);
        float top = t.minY() - lifted - (CLASSIC + 5) * unit;
        float left = center - total / 2 - 2 * unit;
        return new float[]{left, top, left + total + 4 * unit, top + (CLASSIC + 4) * unit};
    }

    public float tagRounding(float unit) {
        return esp.nameStyle().get() == 0 ? (ROUND - 1) * unit : 0;
    }

    private void modern(ImDrawList draw, Target t, Pass pass) {
        ImFont font = Fonts.regular();
        float nameSize = NAME * TEXT_SCALE * u;
        float nameWidth = width(font, nameSize, t.name());
        boolean health = esp.nameHealth().get() && t.living();
        float center = (t.minX() + t.maxX()) / 2;
        float[] rect = tag(t, u);
        float left = rect[0];
        float top = rect[1];
        float right = rect[2];
        float height = rect[3] - top;
        boolean overlays = esp.nameOverlays().get();
        if (pass == Pass.BLUR && overlays) Effects.blur(draw, left, top, right, top + height, ROUND * u, 0);
        if (pass == Pass.SHADOW && overlays) {
            Effects.shadow(draw, left + u / 2, top + u / 2, right - u / 2, top + height - u / 2, ROUND * u, 0, t.strength() ? STRENGTH_GLOW : BLACK);
        }
        if (pass != Pass.FILL) return;
        int background = esp.nameBackground().get();
        if (t.strength()) background = Colors.mix(background, Colors.reAlpha(STRENGTH_TINT, Colors.alpha(background)), STRENGTH_MIX);
        draw.addRectFilled(left, top, right, top + height, background, (ROUND - 1) * u);
        float healthSize = HEALTH * TEXT_SCALE * u;
        float y = top + (height - nameSize - (health ? healthSize : 0)) / 2;
        Controls.addText(draw, font, nameSize, center - nameWidth / 2, y, esp.nameColor().get(), t.name());
        if (!health) return;
        String value = String.valueOf((int) t.health());
        Controls.addText(draw, font, healthSize, center - width(font, healthSize, value) / 2, y + nameSize, WHITE, value);
    }

    private void classic(ImDrawList draw, Target t, Pass pass) {
        if (pass != Pass.FILL) return;
        ImFont font = Fonts.regular();
        float size = CLASSIC * TEXT_SCALE * u;
        ImFont icons = Fonts.icons();
        float iconSize = size * HEART_SCALE;
        float[] rect = tag(t, u);
        draw.addRectFilled(rect[0], rect[1], rect[2], rect[3], esp.nameBackground().get());
        float x = rect[0] + 2 * u;
        float y = rect[1] + ((CLASSIC + 4) * u - size) / 2;
        x = text(draw, font, size, x, y, WHITE, t.name());
        if (!t.living()) return;
        String value = String.valueOf(Math.round(t.health()));
        x = text(draw, font, size, x, y, GRAY, OPEN);
        x = text(draw, icons, iconSize, x, y + (size - iconSize) / 2, DARK_RED, HEART);
        x = text(draw, font, size, x, y, DARK_RED, value);
        text(draw, font, size, x, y, GRAY, CLOSE);
    }

    private static float classicWidth(Target t, float size) {
        ImFont font = Fonts.regular();
        float total = width(font, size, t.name());
        if (!t.living()) return total;
        String value = String.valueOf(Math.round(t.health()));
        return total + width(font, size, OPEN) + width(Fonts.icons(), size * HEART_SCALE, HEART) + width(font, size, value) + width(font, size, CLOSE);
    }

    private static int accent(ColorValue first, ColorValue second, float y) {
        double blend = Math.sin(System.currentTimeMillis() / 600.0 + y * 0.06) * 0.5 + 0.5;
        return Colors.mix(second.get(), first.get(), (float) blend);
    }

    private static int hue(float hue) {
        float h = (hue - (float) Math.floor(hue)) * 6;
        int sector = (int) h;
        float f = h - sector;
        float q = 1 - f;
        float[] rgb = switch (sector) {
            case 0 -> new float[]{1, f, 0};
            case 1 -> new float[]{q, 1, 0};
            case 2 -> new float[]{0, 1, f};
            case 3 -> new float[]{0, q, 1};
            case 4 -> new float[]{f, 0, 1};
            default -> new float[]{1, 0, q};
        };
        return Colors.rgb((int) (rgb[0] * 255 + .5F), (int) (rgb[1] * 255 + .5F), (int) (rgb[2] * 255 + .5F));
    }

    private static void glow(ImDrawList draw, float x, float y, float w, float h, int color) {
        Effects.shadow(draw, x, y, x + w, y + h, 0, 0, color);
    }

    private static void rect(ImDrawList draw, float x, float y, float w, float h, int color) {
        draw.addRectFilled(x, y, x + w, y + h, color);
    }

    private static void across(ImDrawList draw, float x, float y, float w, float h, int from, int to) {
        int start = Natives.vertexCount(draw.ptr);
        draw.addRectFilled(x, y, x + w, y + h, WHITE);
        Natives.gradient(draw.ptr, start, x, y, x + w, y, from, to);
    }

    private static void down(ImDrawList draw, float x, float y, float w, float h, int from, int to) {
        int start = Natives.vertexCount(draw.ptr);
        draw.addRectFilled(x, y, x + w, y + h, WHITE);
        Natives.gradient(draw.ptr, start, x, y, x, y + h, from, to);
    }

    private static float text(ImDrawList draw, ImFont font, float size, float x, float y, int color, String text) {
        Controls.addText(draw, font, size, x, y, color, text);
        return x + width(font, size, text);
    }

    private static float width(ImFont font, float size, String text) {
        return font.calcTextSizeAX(size, Float.MAX_VALUE, 0, text);
    }

    public enum Pass {
        BLUR,
        SHADOW,
        FILL
    }
}

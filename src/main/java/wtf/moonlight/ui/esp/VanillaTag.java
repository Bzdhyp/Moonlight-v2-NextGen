package wtf.moonlight.ui.esp;

import wtf.moonlight.ui.imgui.Fonts;

public final class VanillaTag {
    private static final float LINE = .225F;
    private static final float PAD = .025F;

    private VanillaTag() {
    }

    public static float size(float scale) {
        return LINE * scale;
    }

    public static float[] bounds(Kind kind, View view, String text, float x, float y, float scale) {
        float anchorX = x + view.x(0, 0) * scale;
        float anchorY = y + view.y(0, kind.tag(), 0) * scale;
        float half = Fonts.regular().calcTextSizeAX(size(scale), Float.MAX_VALUE, 0, text) / 2 + PAD * scale;
        return new float[]{anchorX - half, anchorY - PAD * scale, anchorX + half, anchorY + LINE * scale};
    }
}

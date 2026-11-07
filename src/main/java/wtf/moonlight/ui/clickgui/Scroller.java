package wtf.moonlight.ui.clickgui;

import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;

import static wtf.moonlight.ui.clickgui.Widgets.scale;

final class Scroller {
    private static final float STEP = 74;
    private static final float SPEED = 16;

    private int generation;
    private float target;
    private float shown;

    void update(int generation) {
        if (generation != this.generation) {
            this.generation = generation;
            target = 0;
            shown = 0;
        }
        if (ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) target -= ImGui.getIO().getMouseWheel() * scale(STEP);
        target = Math.clamp(target, 0, ImGui.getScrollMaxY());
        shown += (target - shown) * (1 - (float) Math.exp(-SPEED * ImGui.getIO().getDeltaTime()));
        if (Math.abs(target - shown) < .5F) shown = target;
        ImGui.setScrollY(shown);
    }
}

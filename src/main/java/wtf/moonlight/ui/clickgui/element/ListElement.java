package wtf.moonlight.ui.clickgui.element;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.ui.clickgui.Colors;
import wtf.moonlight.ui.clickgui.Controls;
import wtf.moonlight.ui.clickgui.Panel;
import wtf.moonlight.ui.clickgui.Theme;
import wtf.moonlight.ui.clickgui.Widgets;
import wtf.moonlight.ui.imgui.Fonts;
import wtf.moonlight.value.impl.BoolValue;
import wtf.moonlight.value.impl.ListValue;

import java.util.ArrayList;
import java.util.List;

import static wtf.moonlight.ui.clickgui.Widgets.scale;

public final class ListElement extends Element {
    private static final float ROW = 32;
    private static final float SUB_ROW = 28;
    private static final float SUB_TEXT = 13;
    private static final float WIDTH = 188;
    private static final float PLAIN_WIDTH = 160;
    private static final float ICON = 14;
    private static final float SLIDE = 18;
    private static final float DRAG_START = 4;
    private static final double LONG_PRESS = 0.25;
    private static final String GRIP = Character.toString(0xF58E);

    private final ListValue value;
    private @Nullable String held;
    private @Nullable BoolValue pressed;
    private @Nullable String open;
    private @Nullable String unfolded;
    private float reveal;
    private float grab;
    private float pressY;
    private double pressedAt;
    private boolean dragging;

    public ListElement(Panel panel, ListValue value) {
        super(panel);
        this.value = value;
    }

    @Override
    public float width() {
        return scale(134);
    }

    @Override
    public float height() {
        return scale(23);
    }

    @Override
    public void draw() {
        Theme theme = panel.colors();
        List<String> shown = new ArrayList<>();
        for (String option : value.getOptions()) {
            if (value.has(option)) shown.add(option);
        }
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        if (Controls.select(ImGui.getWindowDrawList(), 0, x, y, width(), shown.isEmpty() ? "None" : String.join(", ", shown))) {
            ImGui.openPopup("##list");
        }
        reveal += ((open != null ? 1 : 0) - reveal) * (1 - (float) Math.exp(-SLIDE * ImGui.getIO().getDeltaTime()));
        if (open != null) unfolded = open;
        else if (reveal < .001F) unfolded = null;
        float step = scale(ROW);
        ImGui.setNextWindowSize(scale(value.isSortable() ? WIDTH : PLAIN_WIDTH), scale(8) + step * value.getOptions().size() + unfoldedHeight());
        if (!Widgets.beginPopup(theme, "##list", ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            held = null;
            pressed = null;
            dragging = false;
            open = null;
            unfolded = null;
            reveal = 0;
            return;
        }
        rows(theme, step);
        Widgets.endPopup();
    }

    private float unfoldedHeight() {
        String option = unfolded;
        return option == null ? 0 : reveal * scale(SUB_ROW) * value.extras(option).size();
    }

    private void rows(Theme theme, float step) {
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float width = ImGui.getContentRegionAvailX();
        List<String> options = value.getOptions();
        String parent = unfolded;
        List<BoolValue> subs = parent == null ? List.of() : value.extras(parent);
        int parentIndex = parent == null ? -1 : options.indexOf(parent);
        float sub = scale(SUB_ROW);
        float extra = unfoldedHeight();
        float gap = (parentIndex + 1) * step;
        ImGui.invisibleButton("##rows", width, step * options.size() + extra);
        boolean hovered = ImGui.isItemHovered();
        float mouseY = ImGui.getMousePosY();
        float local = mouseY - top;
        boolean inside = parentIndex >= 0 && local >= gap && local < gap + extra;
        int subUnder = inside ? Math.min((int) ((local - gap) / sub), subs.size() - 1) : -1;
        float flat = parentIndex >= 0 && local >= gap + extra ? local - extra : local;
        int under = inside ? parentIndex : Math.clamp((int) (flat / step), 0, options.size() - 1);
        if (ImGui.isItemActivated()) {
            if (inside) {
                pressed = subs.get(subUnder);
            } else {
                held = options.get(under);
                grab = flat - under * step;
                pressY = mouseY;
                pressedAt = ImGui.getTime();
                dragging = false;
            }
        }
        if (hovered && !inside && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            String option = options.get(under);
            if (!value.extras(option).isEmpty()) open = option.equals(open) ? null : option;
        }
        String current = held;
        if (current != null && value.isSortable() && ImGui.isItemActive() && !dragging
                && (ImGui.getTime() - pressedAt >= LONG_PRESS || Math.abs(mouseY - pressY) > scale(DRAG_START))) {
            dragging = true;
            open = null;
        }
        float lifted = Math.clamp(mouseY - grab - top, 0, step * (options.size() - 1));
        if (current != null && dragging) {
            int slot = Math.round(lifted / step);
            if (options.indexOf(current) != slot) value.move(current, slot);
        }
        if (ImGui.isItemDeactivated()) {
            BoolValue state = pressed;
            if (state != null) state.set(!state.get());
            if (current != null && !dragging) value.toggle(current);
            held = null;
            pressed = null;
            dragging = false;
        }

        ImDrawList draw = ImGui.getWindowDrawList();
        options = value.getOptions();
        parentIndex = parent == null ? -1 : options.indexOf(parent);
        String carried = dragging ? held : null;
        float carriedGrip = 0;
        float parentTop = 0;
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            int id = ImGui.getID("##slot" + option);
            boolean moving = option.equals(carried);
            float offset = moving ? lifted : Widgets.motion(id, i * step, SLIDE) + (parentIndex >= 0 && i > parentIndex ? extra : 0);
            if (moving) ImGui.getStateStorage().setFloat(id, lifted);
            if (i == parentIndex) parentTop = offset;
            float hover = Widgets.motion(id ^ 0x5A17, !dragging && hovered && !inside && i == under ? 1 : 0, 22);
            float grip = Widgets.motion(id ^ 0x6A11, moving ? 1 : 0, 22);
            if (moving) carriedGrip = grip;
            else row(draw, theme, option, left, top + offset, width, hover, grip, false);
        }
        if (parentIndex >= 0 && extra > .5F) {
            float subTop = top + parentTop + step;
            draw.pushClipRect(left, subTop, left + width, subTop + extra, true);
            for (int j = 0; j < subs.size(); j++) {
                float hover = Widgets.motion(ImGui.getID("##sub" + j) ^ 0x3B21, !dragging && hovered && inside && j == subUnder ? 1 : 0, 22);
                subRow(draw, theme, subs.get(j), left, subTop + j * sub, width, hover);
            }
            draw.popClipRect();
        }
        if (carried != null) row(draw, theme, carried, left, top + lifted, width, 0, carriedGrip, true);
    }

    private void row(ImDrawList draw, Theme theme, String option, float x, float y, float width, float hover, float grip, boolean lifted) {
        float height = scale(ROW);
        int fill = lifted ? theme.selectorElementActive : Colors.reAlpha(theme.selectorElementHover, (int) (Colors.alpha(theme.selectorElementHover) * hover));
        draw.addRectFilled(x, y, x + width, y + height, fill, scale(12));
        boolean on = value.has(option);
        int color = on ? theme.textHighlight : theme.main;
        if (on) Controls.check(draw, x + scale(14), y + height / 2, theme.textHighlight);
        String icon = value.icon(option);
        if (icon != null) {
            ImGui.pushFont(Fonts.hudIcons(), ICON * Fonts.HUD_ICON_EM);
            draw.addText(x + scale(30), y + (height - Widgets.textHeight()) * .5f, color, icon);
            Widgets.popFont();
        }
        Widgets.pushText(Widgets.TEXT_CONTROL);
        draw.addText(x + scale(icon != null ? 52 : 35), y + (height - Widgets.textHeight()) * .5f, color, option);
        Widgets.popFont();
        if (!value.extras(option).isEmpty()) {
            float turn = option.equals(unfolded) ? reveal : 0;
            arrow(draw, x + width - scale(value.isSortable() ? 40 : 18), y + height / 2, turn, Colors.interpolate(theme.secondary, theme.textHighlight, Math.max(hover, turn)));
        }
        if (!value.isSortable()) return;
        Widgets.pushIcon();
        draw.addText(x + width - scale(22), y + (height - Widgets.textHeight()) * .5f, Colors.interpolate(theme.secondary, theme.textHighlight, grip), GRIP);
        Widgets.popFont();
    }

    private void subRow(ImDrawList draw, Theme theme, BoolValue state, float x, float y, float width, float hover) {
        float height = scale(SUB_ROW);
        draw.addRectFilled(x + scale(24), y + scale(2), x + width, y + height - scale(2),
                Colors.reAlpha(theme.selectorElementHover, (int) (Colors.alpha(theme.selectorElementHover) * hover)), scale(10));
        boolean on = state.get();
        if (on) Controls.check(draw, x + scale(34), y + height / 2, theme.textHighlight);
        Widgets.pushText(SUB_TEXT);
        draw.addText(x + scale(52), y + (height - Widgets.textHeight()) * .5f, on ? theme.textHighlight : theme.main, state.getName());
        Widgets.popFont();
    }

    private static void arrow(ImDrawList draw, float x, float y, float turn, int color) {
        float angle = turn * (float) Math.PI / 2;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float[] xs = {-1.5F, 1.5F, -1.5F};
        float[] ys = {-3, 0, 3};
        float[] px = new float[3];
        float[] py = new float[3];
        for (int i = 0; i < 3; i++) {
            px[i] = x + scale(xs[i] * cos - ys[i] * sin);
            py[i] = y + scale(xs[i] * sin + ys[i] * cos);
        }
        draw.addLine(px[0], py[0], px[1], py[1], color, scale(1.3F));
        draw.addLine(px[1], py[1], px[2], py[2], color, scale(1.3F));
    }
}

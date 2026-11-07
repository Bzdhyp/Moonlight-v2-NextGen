package wtf.moonlight.ui.clickgui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import wtf.moonlight.module.Module;
import wtf.moonlight.module.Subcategory;
import wtf.moonlight.ui.imgui.Effects;
import wtf.moonlight.ui.imgui.Fonts;
import wtf.moonlight.ui.imgui.Natives;
import wtf.moonlight.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static wtf.moonlight.ui.clickgui.Widgets.scale;

final class Search {
    private static final float LEFT = 168;
    private static final float TOP = 10;
    private static final float WIDTH = 572;
    private static final float BAR = 43;
    private static final float FULL = 558;
    private static final float ROUNDING = 10;
    private static final float ICON_X = 22;
    private static final float TEXT_X = 42;
    private static final float CLOSE_X = 26;
    private static final float BUTTON = 24;
    private static final float ICON = 15;
    private static final float LINE_INSET = 13;
    private static final float LIST_TOP = 54;
    private static final float LIST_INSET = 14;
    private static final float LIST_BOTTOM = 8;
    private static final float CARD_TITLE = 20;
    private static final float CRUMB = 12;
    private static final float CRUMB_CENTER = 9;
    private static final float CRUMB_GAP = 8;
    private static final float FADE = 18;
    private static final float GROW = 14;
    private static final String MAGNIFIER = Character.toString(0xF002);
    private static final String CLOSE = Character.toString(0xF00D);
    private static final String LINK = Character.toString(0xF0C1);

    private final Panel panel;
    private final PageLayout layout;
    private final ImString buffer = new ImString(63);
    private final Scroller scroller = new Scroller();
    private final List<Result> results = new ArrayList<>();
    private String query = "";
    private float shown;
    private float height = BAR;
    private boolean focus;

    Search(Panel panel, PageLayout layout) {
        this.panel = panel;
        this.layout = layout;
    }

    boolean visible() {
        return shown > 0 || panel.isSearching();
    }

    void reset() {
        shown = 0;
    }

    void render(Theme theme, float x, float y, float maxX, float maxY, float rounding) {
        float delta = ImGui.getIO().getDeltaTime();
        boolean open = panel.isSearching();
        if (open && shown == 0) {
            buffer.set("");
            height = BAR;
            focus = true;
        }
        shown += ((open ? 1 : 0) - shown) * (1 - (float) Math.exp(-FADE * delta));
        if (Math.abs((open ? 1 : 0) - shown) < .005F) shown = open ? 1 : 0;
        if (shown == 0) return;
        rebuild();
        float target = query.isEmpty() ? BAR : FULL;
        height += (target - height) * (1 - (float) Math.exp(-GROW * delta));
        if (Math.abs(target - height) < .5F) height = target;

        ImGui.setCursorScreenPos(x, y);
        Windows.beginChild("search", maxX - x, maxY - y, ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = x + scale(LEFT);
        float top = y + scale(TOP);
        float right = left + scale(WIDTH);
        float bottom = top + scale(height);
        float round = scale(ROUNDING);
        Effects.blur(draw, x, y, maxX, maxY, rounding, 0);
        Effects.shadow(draw, left, top, right, bottom, round, 0, theme.fade(Controls.SHADOW));
        draw.addRectFilled(left, top, right, bottom, theme.windowBg, round);
        draw.addRectFilled(left, top, right, bottom, theme.contentBg, round);
        draw.addRect(left, top, right, bottom, theme.contentBorder, round, 0, scale(1));
        bar(theme, draw, left, top, right);
        if (height > BAR + 1) {
            draw.addLine(left + scale(LINE_INSET), top + scale(BAR), right - scale(LINE_INSET), top + scale(BAR), theme.split, scale(1));
            list(theme, left, top, bottom);
        }
        if (open && ImGui.isKeyPressed(ImGuiKey.Escape)) panel.setSearching(false);
        if (shown < 1) {
            for (long list : Windows.subtree()) Natives.transform(list, 0, 0, 1, 0, 0, shown);
        }
        Windows.endChild();
    }

    private void bar(Theme theme, ImDrawList draw, float left, float top, float right) {
        float center = top + scale(BAR) / 2;
        ImGui.pushFont(Fonts.icons(), ICON);
        draw.addText(left + scale(ICON_X) - Widgets.textWidth(MAGNIFIER) / 2, center - Widgets.textHeight() / 2, theme.secondary, MAGNIFIER);
        ImGui.setCursorScreenPos(right - scale(CLOSE_X + BUTTON / 2), center - scale(BUTTON / 2));
        if (ImGui.invisibleButton("close", scale(BUTTON), scale(BUTTON))) panel.setSearching(false);
        float hover = Widgets.motion(ImGui.getItemID() ^ 0x5c10, ImGui.isItemHovered() ? 1 : 0, 18);
        draw.addText(right - scale(CLOSE_X) - Widgets.textWidth(CLOSE) / 2, center - Widgets.textHeight() / 2,
                Colors.mix(theme.secondary, theme.textHighlight, hover), CLOSE);
        Widgets.popFont();

        Widgets.pushText(Widgets.TEXT_BODY);
        float textX = left + scale(TEXT_X);
        ImGui.setCursorScreenPos(textX, center - Widgets.textHeight() / 2);
        ImGui.setNextItemWidth(right - scale(CLOSE_X + BUTTON) - textX);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.textHighlight);
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, Colors.reAlpha(theme.iconHighlight, Controls.FIELD_SELECTION_ALPHA));
        ImGui.pushStyleColor(ImGuiCol.NavCursor, 0);
        if (focus) {
            ImGui.setKeyboardFocusHere();
            focus = false;
        }
        ImGui.inputText("##query", buffer);
        ImGui.popStyleColor(6);
        ImGui.popStyleVar(2);
        Widgets.popFont();
    }

    private void list(Theme theme, float left, float top, float bottom) {
        float listTop = top + scale(LIST_TOP);
        float listHeight = bottom - scale(LIST_BOTTOM) - listTop;
        if (listHeight < 1) return;
        float width = scale(WIDTH - LIST_INSET * 2);
        ImGui.setCursorScreenPos(left + scale(LIST_INSET), listTop);
        Windows.beginChild("results", width, listHeight, ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        scroller.update(query.hashCode());
        if (results.isEmpty()) {
            Widgets.label(ImGui.getWindowDrawList(), ImGui.getCursorScreenPosX() + scale(6), ImGui.getCursorScreenPosY(), theme.secondary, "No matching modules", 14);
        }
        for (Result result : results) {
            float cardTop = ImGui.getCursorScreenPosY() + scale(CARD_TITLE);
            crumb(theme, result.module, ImGui.getCursorScreenPosX() + scale(6), cardTop - scale(CRUMB_CENTER));
            PageLayout.card(theme, result.module.getName(), "", result.rows, width);
        }
        Windows.endChild();
    }

    private void crumb(Theme theme, Module module, float x, float center) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float startX = ImGui.getCursorScreenPosX();
        float startY = ImGui.getCursorScreenPosY();
        int color = theme.caption;
        Widgets.pushText(CRUMB);
        float textY = center - Widgets.textHeight() / 2;
        float cursor = x;
        String category = module.getCategory().getName().toUpperCase(Locale.ROOT);
        draw.addText(cursor, textY, color, category);
        cursor += Widgets.textWidth(category) + scale(4);
        Subcategory sub = module.getSubcategory();
        if (sub != null) {
            draw.addLine(cursor, center - scale(2), cursor + scale(2), center, color, scale(1));
            draw.addLine(cursor + scale(2), center, cursor, center + scale(2), color, scale(1));
            cursor += scale(6);
            String name = sub.getName().toUpperCase(Locale.ROOT);
            draw.addText(cursor, textY, color, name);
            cursor += Widgets.textWidth(name);
        }
        cursor += scale(CRUMB_GAP);
        Controls.chevron(draw, cursor, center - scale(3), color);
        cursor += scale(3 + CRUMB_GAP);
        String name = module.getName().toUpperCase(Locale.ROOT);
        draw.addText(cursor, textY, color, name);
        cursor += Widgets.textWidth(name) + scale(CRUMB_GAP);
        Widgets.popFont();
        ImGui.pushFont(Fonts.icons(), CRUMB);
        float linkWidth = Widgets.textWidth(LINK);
        ImGui.setCursorScreenPos(cursor - scale(3), center - scale(BUTTON / 2) + scale(4));
        ImGui.pushID(module.getName());
        if (ImGui.invisibleButton("link", linkWidth + scale(6), scale(BUTTON) - scale(8))) layout.reveal(module);
        float hover = Widgets.motion(ImGui.getItemID() ^ 0x11c4, ImGui.isItemHovered() ? 1 : 0, 18);
        ImGui.popID();
        draw.addText(cursor, center - Widgets.textHeight() / 2, Colors.mix(color, theme.textHighlight, hover), LINK);
        Widgets.popFont();
        ImGui.setCursorScreenPos(startX, startY);
    }

    private void rebuild() {
        String text = buffer.get().strip().toLowerCase(Locale.ROOT);
        if (text.equals(query)) return;
        query = text;
        results.clear();
        if (text.isEmpty()) return;
        for (Module module : panel.modules().getModules()) {
            if (!module.getName().toLowerCase(Locale.ROOT).contains(text)) continue;
            List<Row> rows = new ArrayList<>();
            rows.add(Row.enabled(panel, module));
            for (Value<?> value : module.getValues()) rows.add(Row.of(panel, value, false));
            results.add(new Result(module, rows));
        }
    }

    private record Result(Module module, List<Row> rows) {
    }
}

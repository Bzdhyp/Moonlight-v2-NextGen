package wtf.moonlight.ui.clickgui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.module.impl.render.Esp;
import wtf.moonlight.ui.clickgui.element.ColorElement;
import wtf.moonlight.ui.clickgui.element.Palette;
import wtf.moonlight.ui.clickgui.element.SelectorElement;
import wtf.moonlight.ui.clickgui.element.SwitchElement;
import wtf.moonlight.ui.esp.Kind;
import wtf.moonlight.ui.esp.Models;
import wtf.moonlight.ui.esp.Painter;
import wtf.moonlight.ui.esp.Target;
import wtf.moonlight.ui.esp.VanillaTag;
import wtf.moonlight.ui.esp.View;
import wtf.moonlight.ui.imgui.Effects;
import wtf.moonlight.value.impl.BoolValue;
import wtf.moonlight.value.impl.ColorValue;
import wtf.moonlight.value.impl.ModeValue;

import java.util.List;
import java.util.Map;

import static wtf.moonlight.ui.clickgui.Widgets.scale;

final class EspPane {
    static final float CARD_WIDTH = 259;
    static final float LIST_WIDTH = 268;

    private static final float LEFT = 440;
    private static final float FULL_LEFT = 167;
    private static final float RIGHT = 735;
    private static final float BAR_Y = 90;
    private static final float CHIP = 24;
    private static final float CHIP_PAD = 9;
    private static final float BUTTON = 26;
    private static final float BUTTON_GAP = 4;
    private static final float MODEL_TOP = 136;
    private static final float MODEL_BOTTOM = 556;
    private static final float MAX_WIDTH = 170;
    private static final float MAX_HEIGHT = 300;
    private static final float MAX_SCALE = 150;
    private static final float SIDE = .07F;
    private static final float END = .105F;
    private static final float DRAG = 3;
    private static final float TURN = .012F;
    private static final float TURN_RATE = 18;
    private static final float PITCH = (float) (Math.PI / 2);
    private static final float ZOOM_STEP = 1.15F;
    private static final float MIN_ZOOM = .5F;
    private static final float MAX_ZOOM = 3;
    private static final float SLIDE_TIME = .3F;
    private static final float OUTLINE_GAP = 1.5F;
    private static final float LINE_BAND = 2;
    private static final int OUTLINE_ALPHA = 170;
    private static final int TAG_TINT = 45;
    private static final float WIDEN = 14;
    private static final float KIND_WIDTH = 132;
    private static final float KIND_ROW = 30;
    private static final float UNIT = 2;
    private static final float ITEMS_TOP = 110;
    private static final float ITEMS_PAD = 8;
    private static final float ITEMS_TITLE = 34;
    private static final float CHIP_ROW = 28;
    private static final float CHIP_GAP = 6;
    private static final float CHIP_TEXT_PAD = 33;
    private static final float CHIP_CHECK = 9;
    private static final float CHIP_TEXT = 23;
    private static final float POPUP_RATE = 20;
    private static final float POPUP_ROW = 30;
    private static final float POPUP_PAD = 8;
    private static final String HIDE = Character.toString(0xF503);
    private static final String LIST = Character.toString(0xF022);
    private static final String EXPAND = Character.toString(0xF424);
    private static final List<String> ELEMENTS = List.of(Esp.NAME_TAG, Esp.HEALTH_BAR, Esp.ARMOR_BAR, Esp.BOX);
    private static final Map<Kind, String> NAMES = Map.of(Kind.MOB, "Zombie", Kind.ITEM, "Diamond Sword", Kind.TNT, "Primed TNT",
            Kind.CRYSTAL, "End Crystal", Kind.FIREBALL, "Fireball");

    private final Panel panel;
    private final Esp esp;
    private final Painter painter;
    private final Map<String, List<Row>> rows;
    private final Map<String, ColorValue> colors;
    private Kind kind = Kind.PLAYER;
    private boolean hidden;
    private boolean items;
    private float itemsReveal;
    private boolean full;
    private float wide;
    private float yaw;
    private float pitch;
    private float zoom = 1;
    private float shownYaw;
    private float shownPitch;
    private float shownZoom = 1;
    private float dragged;
    private @Nullable String moving;
    private final Slide healthSlide = new Slide();
    private final Slide armorSlide = new Slide();
    private String element = Esp.BOX;
    private float @Nullable [] tag;
    private float elementMinX;
    private float elementMinY;
    private float elementMaxX;
    private float elementMaxY;

    EspPane(Panel panel, Esp esp) {
        this.panel = panel;
        this.esp = esp;
        this.painter = new Painter(esp);
        this.colors = Map.of(Esp.BOX, esp.boxColor(), Esp.HEALTH_BAR, esp.healthColor(), Esp.ARMOR_BAR, esp.armorColor(), Esp.NAME_TAG, esp.nameColor());
        this.rows = Map.of(
                Esp.BOX, List.of(color("Second Color", esp.boxSecond()), mode("Mode", esp.boxMode()), toggle("Glow", esp.boxGlow())),
                Esp.HEALTH_BAR, List.of(color("Second Color", esp.healthSecond()), mode("Mode", esp.healthMode()), mode("Side", esp.healthSide()),
                        toggle("Glow", esp.healthGlow())),
                Esp.ARMOR_BAR, List.of(color("Second Color", esp.armorSecond()), mode("Side", esp.armorSide()), toggle("Glow", esp.armorGlow())),
                Esp.NAME_TAG, List.of(color("Background", esp.nameBackground()), mode("Style", esp.nameStyle()), toggle("Health", esp.nameHealth()),
                        toggle("Overlays", esp.nameOverlays())));
    }

    float wide() {
        return wide;
    }

    void render(Theme theme, float originX, float originY) {
        wide += ((full ? 1 : 0) - wide) * (1 - (float) Math.exp(-WIDEN * ImGui.getIO().getDeltaTime()));
        if (Math.abs((full ? 1 : 0) - wide) < .001F) wide = full ? 1 : 0;
        float ease = 1 - (float) Math.pow(1 - wide, 3);
        float left = originX + scale(LEFT + (FULL_LEFT - LEFT) * ease);
        float right = originX + scale(RIGHT);
        float center = (left + right) / 2;
        ImDrawList draw = ImGui.getWindowDrawList();
        toolbar(theme, draw, center, originY + scale(BAR_Y));

        float top = originY + scale(MODEL_TOP);
        float bottom = originY + scale(MODEL_BOTTOM);
        float blend = 1 - (float) Math.exp(-TURN_RATE * ImGui.getIO().getDeltaTime());
        shownYaw += (yaw - shownYaw) * blend;
        shownPitch += (pitch - shownPitch) * blend;
        shownZoom += (zoom - shownZoom) * blend;
        float size = shownZoom * Math.min(scale(MAX_SCALE), Math.min(scale(MAX_WIDTH) / (2 * (kind.half() + SIDE)),
                scale(MAX_HEIGHT) / (kind.top() - kind.bottom() + 2 * END)));
        float middle = (top + bottom) / 2;
        View view = new View(kind, shownYaw, shownPitch);
        draw.pushClipRect(left, top, right, bottom, true);
        String name = model(draw, view, left, top, right, bottom, size);
        Target target = target(name, view, center, middle, size);
        float unit = scale(UNIT);
        place(target, unit);

        String hovered = items ? null : hit(target, unit);
        boolean over = false;
        boolean clicked = false;
        if (!items) {
            ImGui.setCursorScreenPos(left, top);
            clicked = ImGui.invisibleButton("##esp-model", right - left, bottom - top) && dragged <= scale(DRAG);
            if (ImGui.isItemActivated()) {
                dragged = 0;
                moving = Esp.HEALTH_BAR.equals(hovered) || Esp.ARMOR_BAR.equals(hovered) ? hovered : null;
            }
            boolean dragging = ImGui.isItemActive() && drag(target);
            if (!ImGui.isItemActive()) moving = null;
            if (dragging) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll);
            float wheel = ImGui.getIO().getMouseWheel();
            if (ImGui.isItemHovered() && wheel != 0) zoom = Math.clamp(zoom * (float) Math.pow(ZOOM_STEP, wheel), MIN_ZOOM, MAX_ZOOM);
            over = !dragging && ImGui.isItemHovered() && hovered != null;
        }
        if (over) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        String focus = moving != null ? moving : over ? hovered : ImGui.isPopupOpen("##esp-element") ? element : null;
        if (over && clicked) {
            element = hovered;
            float[] box = bounds(target, unit, hovered);
            elementMinX = box[0];
            elementMinY = box[1];
            elementMaxX = box[2];
            elementMaxY = box[3];
            ImGui.openPopup("##esp-element");
        }
        for (Painter.Pass pass : Painter.Pass.values()) painter.paint(draw, target, unit, pass);
        if (focus != null) outline(theme, draw, target, unit, focus);
        draw.popClipRect();
        items(theme, left, right, originY);
        elementPopup(theme);
    }

    private void toolbar(Theme theme, ImDrawList draw, float center, float middle) {
        Widgets.pushText(Widgets.TEXT_CONTROL);
        float label = Widgets.textWidth(kind.plural());
        Widgets.popFont();
        Widgets.pushIcon();
        float icon = Widgets.textWidth(kind.icon());
        Widgets.popFont();
        float chip = scale(CHIP_PAD * 2 + 6) + icon + label;
        float total = chip + (scale(BUTTON + BUTTON_GAP)) * 3;
        float x = center - total / 2;
        float y = middle - scale(CHIP) / 2;
        ImGui.setCursorScreenPos(x, y);
        if (ImGui.invisibleButton("##esp-kind", chip, scale(CHIP))) ImGui.openPopup("##esp-kinds");
        float hover = Widgets.motion(ImGui.getItemID() ^ 0x2b11, ImGui.isItemHovered() ? 1 : 0, 18);
        draw.addRectFilled(x, y, x + chip, y + scale(CHIP), Colors.reAlpha(theme.iconHighlight, (int) (40 + 20 * hover)), scale(6));
        Widgets.pushIcon();
        draw.addText(x + scale(CHIP_PAD), middle - Widgets.textHeight() / 2, theme.iconHighlight, kind.icon());
        Widgets.popFont();
        Widgets.pushText(Widgets.TEXT_CONTROL);
        draw.addText(x + scale(CHIP_PAD + 6) + icon, middle - Widgets.textHeight() / 2, theme.iconHighlight, kind.plural());
        Widgets.popFont();
        kinds(theme, x, y + scale(CHIP + 6));
        x += chip + scale(BUTTON_GAP);
        if (button(theme, draw, "##esp-hide", HIDE, x, y, hidden)) hidden = !hidden;
        x += scale(BUTTON + BUTTON_GAP);
        if (button(theme, draw, "##esp-items", LIST, x, y, items)) items = !items;
        x += scale(BUTTON + BUTTON_GAP);
        if (button(theme, draw, "##esp-full", EXPAND, x, y, full)) full = !full;
    }

    private boolean button(Theme theme, ImDrawList draw, String id, String icon, float x, float y, boolean active) {
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, scale(BUTTON), scale(CHIP));
        float hover = Widgets.motion(ImGui.getItemID() ^ 0x7f21, ImGui.isItemHovered() ? 1 : 0, 18);
        float on = Widgets.motion(ImGui.getItemID() ^ 0x7f22, active ? 1 : 0, 18);
        int fill = Colors.interpolate(Colors.reAlpha(theme.selectorElementHover, (int) (Colors.alpha(theme.selectorElementHover) * hover)),
                theme.selectorElementActive, on);
        draw.addRectFilled(x, y, x + scale(BUTTON), y + scale(CHIP), fill, scale(6));
        Widgets.pushIcon();
        draw.addText(x + (scale(BUTTON) - Widgets.textWidth(icon)) / 2, y + (scale(CHIP) - Widgets.textHeight()) / 2,
                Colors.interpolate(theme.textHighlight, theme.iconHighlight, on), icon);
        Widgets.popFont();
        return clicked;
    }

    private void kinds(Theme theme, float x, float y) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(scale(KIND_WIDTH), scale(8 + KIND_ROW * Kind.values().length));
        if (!Widgets.beginPopup(theme, "##esp-kinds")) return;
        ImDrawList draw = ImGui.getWindowDrawList();
        float left = ImGui.getWindowPosX() + scale(4);
        for (Kind each : Kind.values()) {
            float top = ImGui.getWindowPosY() + scale(4 + KIND_ROW * each.ordinal());
            ImGui.setCursorScreenPos(left, top);
            ImGui.pushID(each.ordinal());
            boolean clicked = ImGui.invisibleButton("kind", scale(KIND_WIDTH - 8), scale(KIND_ROW));
            float hover = Widgets.motion(ImGui.getItemID() ^ 0x4e31, ImGui.isItemHovered() ? 1 : 0, 22);
            ImGui.popID();
            draw.addRectFilled(left, top, left + scale(KIND_WIDTH - 8), top + scale(KIND_ROW),
                    Colors.reAlpha(theme.selectorElementHover, (int) (Colors.alpha(theme.selectorElementHover) * hover)), scale(12));
            int color = each == kind ? theme.textHighlight : Colors.mix(theme.secondary, theme.main, hover);
            Widgets.pushIcon();
            draw.addText(left + scale(12), top + (scale(KIND_ROW) - Widgets.textHeight()) / 2, color, each.icon());
            Widgets.popFont();
            Widgets.pushText(Widgets.TEXT_CONTROL);
            draw.addText(left + scale(32), top + (scale(KIND_ROW) - Widgets.textHeight()) / 2, color, each.single());
            Widgets.popFont();
            if (clicked) {
                kind = each;
                yaw = 0;
                pitch = 0;
                zoom = 1;
                shownYaw = 0;
                shownPitch = 0;
                shownZoom = 1;
                healthSlide.reset();
                armorSlide.reset();
                ImGui.closeCurrentPopup();
            }
        }
        Widgets.endPopup();
    }

    private boolean drag(Target target) {
        float dx = ImGui.getIO().getMouseDeltaX();
        float dy = ImGui.getIO().getMouseDeltaY();
        dragged += Math.abs(dx) + Math.abs(dy);
        if (dragged <= scale(DRAG)) return false;
        String bar = moving;
        if (bar != null) {
            ModeValue side = bar.equals(Esp.HEALTH_BAR) ? esp.healthSide() : esp.armorSide();
            int nearest = side(target);
            if (side.get() != nearest) side.select(nearest);
            return true;
        }
        yaw += dx / scale(1) * TURN;
        pitch = Math.clamp(pitch - dy / scale(1) * TURN, -PITCH, PITCH);
        return true;
    }

    private static int side(Target target) {
        float x = (ImGui.getMousePosX() - (target.minX() + target.maxX()) / 2) / Math.max(1, (target.maxX() - target.minX()) / 2);
        float y = (ImGui.getMousePosY() - (target.minY() + target.maxY()) / 2) / Math.max(1, (target.maxY() - target.minY()) / 2);
        if (Math.abs(x) >= Math.abs(y)) return x < 0 ? Esp.LEFT : Esp.RIGHT;
        return y < 0 ? Esp.TOP : Esp.BOTTOM;
    }

    private void place(Target target, float unit) {
        float step = ImGui.getIO().getDeltaTime() / SLIDE_TIME;
        int healthSide = esp.healthSide().get();
        int armorSide = esp.armorSide().get();
        boolean health = painter.showsHealth(target);
        float[] healthTrack = healthSlide.update(painter.track(target, unit, true), healthSide, healthSide, step);
        float[] armorTrack = armorSlide.update(painter.track(target, unit, false), armorSide, armorSide * 2 + (health && healthSide == armorSide ? 1 : 0), step);
        painter.place(health ? healthTrack : null, painter.showsArmor(target) ? armorTrack : null);
    }

    private void outline(Theme theme, ImDrawList draw, Target t, float unit, String name) {
        if (name.equals(Esp.NAME_TAG)) {
            float[] rect = tag != null ? tag : painter.tag(t, unit);
            float rounding = tag != null ? 0 : painter.tagRounding(unit);
            draw.addRectFilled(rect[0], rect[1], rect[2], rect[3], Colors.reAlpha(theme.iconHighlight, TAG_TINT), rounding);
            return;
        }
        if (name.equals(Esp.BOX)) return;
        float gap = scale(OUTLINE_GAP);
        float[] rect = name.equals(Esp.HEALTH_BAR) ? healthSlide.shown : armorSlide.shown;
        draw.addRect(rect[0] - gap, rect[1] - gap, rect[2] + gap, rect[3] + gap, Colors.reAlpha(theme.textHighlight, OUTLINE_ALPHA), 0, 0, 1);
    }

    private String model(ImDrawList draw, View view, float left, float top, float right, float bottom, float size) {
        Models models = panel.models();
        String entity = models != null ? models.name(kind) : null;
        String name = kind == Kind.PLAYER ? panel.user() : entity != null ? entity : NAMES.get(kind);
        boolean vanilla = esp.elements().has(Esp.NAME_TAG) && esp.vanillaNames();
        tag = vanilla ? VanillaTag.bounds(kind, view, name, (left + right) / 2, (top + bottom) / 2, size) : null;
        if (models != null && !hidden) models.draw(draw, kind, view, vanilla ? name : null, left, top, right, bottom, size);
        return name;
    }

    private Target target(String name, View view, float x, float y, float size) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float cornerX = (corner & 1) == 0 ? -kind.half() - SIDE : kind.half() + SIDE;
            float cornerY = (corner & 2) == 0 ? kind.bottom() - END : kind.top() + END;
            float cornerZ = (corner & 4) == 0 ? kind.front() - SIDE : kind.back() + SIDE;
            float screenX = x + view.x(cornerX, cornerZ) * size;
            float screenY = y + view.y(cornerX, cornerY, cornerZ) * size;
            minX = Math.min(minX, screenX);
            minY = Math.min(minY, screenY);
            maxX = Math.max(maxX, screenX);
            maxY = Math.max(maxY, screenY);
        }
        return switch (kind) {
            case PLAYER -> new Target(minX, minY, maxX, maxY, name, true, 16, 20, 12, false);
            case MOB -> new Target(minX, minY, maxX, maxY, name, true, 20, 20, 2, false);
            default -> new Target(minX, minY, maxX, maxY, name, false, 0, 1, 0, false);
        };
    }

    private @Nullable String hit(Target target, float unit) {
        float x = ImGui.getMousePosX();
        float y = ImGui.getMousePosY();
        for (String name : ELEMENTS) {
            if (!shown(target, name)) continue;
            float[] box = bounds(target, unit, name);
            if (x < box[0] || x >= box[2] || y < box[1] || y >= box[3]) continue;
            float band = LINE_BAND * unit;
            boolean inside = x > target.minX() + band && x < target.maxX() - band && y > target.minY() + band && y < target.maxY() - band;
            if (!name.equals(Esp.BOX) || !inside) return name;
        }
        return null;
    }

    private boolean shown(Target target, String name) {
        if (!esp.elements().has(name)) return false;
        if (name.equals(Esp.HEALTH_BAR)) return target.living();
        if (name.equals(Esp.ARMOR_BAR)) return target.living() && target.armor() > 0;
        return true;
    }

    private float[] bounds(Target t, float u, String name) {
        return switch (name) {
            case Esp.NAME_TAG -> {
                if (tag != null) yield tag;
                float[] rect = painter.tag(t, u);
                yield new float[]{rect[0] - u, rect[1] - u, rect[2] + u, rect[3] + u};
            }
            case Esp.HEALTH_BAR -> healthSlide.around(u);
            case Esp.ARMOR_BAR -> armorSlide.around(u);
            default -> new float[]{t.minX() - LINE_BAND * u, t.minY() - LINE_BAND * u, t.maxX() + LINE_BAND * u, t.maxY() + LINE_BAND * u};
        };
    }

    private void items(Theme theme, float left, float right, float originY) {
        itemsReveal += ((items ? 1 : 0) - itemsReveal) * (1 - (float) Math.exp(-POPUP_RATE * ImGui.getIO().getDeltaTime()));
        if (!items && itemsReveal < .002F) {
            itemsReveal = 0;
            return;
        }
        float x = left + scale(ITEMS_PAD);
        float width = right - left - scale(ITEMS_PAD * 2);
        float top = originY + scale(ITEMS_TOP);
        Widgets.pushText(Widgets.TEXT_SMALL);
        float[] widths = new float[ELEMENTS.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = Widgets.textWidth(ELEMENTS.get(i)) + scale(CHIP_TEXT_PAD);
        Widgets.popFont();
        int lines = 1;
        float cursor = 0;
        float inner = width - scale(ITEMS_PAD * 2 + 16);
        for (float chip : widths) {
            if (cursor > 0 && cursor + chip > inner) {
                lines++;
                cursor = 0;
            }
            cursor += chip + scale(CHIP_GAP);
        }
        float group = scale(26) + lines * scale(CHIP_ROW + CHIP_GAP);
        float height = scale(ITEMS_TITLE) + group + scale(ITEMS_PAD);
        float bottom = top + height;
        ImGui.setCursorScreenPos(x, top);
        Windows.beginChild("##esp-items", width, height, ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.pushClipRectFullScreen();
        Effects.blur(draw, x, top, x + width, bottom, scale(12), 0);
        Effects.shadow(draw, x, top, x + width, bottom, scale(12), 0, theme.fade(Controls.SHADOW));
        draw.popClipRect();
        draw.addRectFilled(x, top, x + width, bottom, theme.modulePanelBg, scale(12));
        draw.addRect(x, top, x + width, bottom, theme.cardBorder, scale(12));
        Widgets.label(draw, x + scale(ITEMS_PAD + 8), top + scale(10), theme.textHighlight, "ESP Items", Widgets.TEXT_BODY);
        float groupX = x + scale(ITEMS_PAD);
        float groupY = top + scale(ITEMS_TITLE);
        float groupWidth = width - scale(ITEMS_PAD * 2);
        draw.addRectFilled(groupX, groupY, groupX + groupWidth, groupY + group, Colors.reAlpha(theme.main, 10), scale(8));
        Widgets.label(draw, groupX + scale(10), groupY + scale(6), theme.main, "Main", Widgets.TEXT_SMALL);
        float chipX = groupX + scale(8);
        float chipY = groupY + scale(26);
        cursor = 0;
        for (int i = 0; i < ELEMENTS.size(); i++) {
            String name = ELEMENTS.get(i);
            if (cursor > 0 && cursor + widths[i] > inner) {
                cursor = 0;
                chipY += scale(CHIP_ROW + CHIP_GAP);
            }
            chip(theme, draw, name, chipX + cursor, chipY, widths[i]);
            cursor += widths[i] + scale(CHIP_GAP);
        }
        for (long list : Windows.subtree()) Popups.transform(list, x, top, width, height, itemsReveal, Popups.Motion.DROPDOWN);
        Windows.endChild();
    }

    private void chip(Theme theme, ImDrawList draw, String name, float x, float y, float width) {
        float height = scale(CHIP_ROW - 4);
        int id = ImGui.getID("##chip" + name);
        boolean hovered = false;
        if (items) {
            ImGui.setCursorScreenPos(x, y);
            if (ImGui.invisibleButton("##chip" + name, width, height)) esp.elements().toggle(name);
            hovered = ImGui.isItemHovered();
        }
        float hover = Widgets.motion(id ^ 0x1c55, hovered ? 1 : 0, 18);
        float on = Widgets.motion(id ^ 0x1c56, esp.elements().has(name) ? 1 : 0, 18);
        draw.addRectFilled(x, y, x + width, y + height,
                Colors.reAlpha(theme.selectorElementHover, (int) (Colors.alpha(theme.selectorElementHover) * hover)), scale(6));
        draw.addRect(x, y, x + width, y + height, theme.split, scale(6), 0, scale(1));
        Controls.check(draw, x + scale(CHIP_CHECK), y + height / 2, Colors.reAlpha(theme.textHighlight, (int) (Colors.alpha(theme.textHighlight) * on)));
        Widgets.pushText(Widgets.TEXT_SMALL);
        draw.addText(x + scale(CHIP_TEXT), y + (height - Widgets.textHeight()) / 2, Colors.interpolate(theme.main, theme.textHighlight, on), name);
        Widgets.popFont();
    }

    private void elementPopup(Theme theme) {
        List<Row> list = rows.get(element);
        float width = scale(Palette.WIDTH);
        float height = scale(Palette.HEIGHT + POPUP_ROW * list.size() + POPUP_PAD);
        float x = elementMaxX + scale(10);
        float viewportRight = ImGui.getMainViewport().getWorkPosX() + ImGui.getMainViewport().getWorkSizeX();
        if (x + width > viewportRight - scale(12)) x = elementMinX - scale(10) - width;
        float y = Math.max(ImGui.getMainViewport().getWorkPosY() + scale(12), (elementMinY + elementMaxY) / 2 - height / 2);
        ImGui.setNextWindowPos(x, y, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(width, height);
        if (!Widgets.beginPopup(theme, "##esp-element", 0, Popups.Motion.DROPDOWN, 0, 0)) return;
        Palette.draw(theme, colors.get(element));
        ImGui.setCursorScreenPos(ImGui.getWindowPosX() + scale(POPUP_PAD), ImGui.getWindowPosY() + scale(Palette.HEIGHT));
        Windows.beginChild("##esp-rows", width - scale(POPUP_PAD * 2), scale(POPUP_ROW) * list.size(), ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar);
        for (Row row : list) row.renderCompact();
        Windows.endChild();
        Widgets.endPopup();
    }

    private Row color(String label, ColorValue value) {
        return new Row(panel, label, new ColorElement(panel, value), List.of(), () -> true);
    }

    private Row mode(String label, ModeValue value) {
        return new Row(panel, label, new SelectorElement(panel, value), List.of(), () -> true);
    }

    private Row toggle(String label, BoolValue value) {
        return new Row(panel, label, new SwitchElement(panel, value::get, value::set), List.of(), () -> true);
    }

    private static final class Slide {
        private final float[] from = new float[4];
        private final float[] shown = new float[4];
        private int state = -1;
        private int side;
        private int fromSide;
        private int shownSide;
        private float progress = 1;

        float[] update(float[] target, int nextSide, int next, float step) {
            if (state == -1) {
                state = next;
                side = nextSide;
                shownSide = nextSide;
                progress = 1;
            } else if (next != state) {
                System.arraycopy(shown, 0, from, 0, 4);
                fromSide = shownSide;
                state = next;
                side = nextSide;
                progress = 0;
            }
            progress = Math.min(1, progress + step);
            float eased = progress * progress * (3 - 2 * progress);
            if (progress == 1) {
                System.arraycopy(target, 0, shown, 0, 4);
                shownSide = side;
            } else if (fromSide == side) {
                for (int i = 0; i < 4; i++) shown[i] = from[i] + (target[i] - from[i]) * eased;
                shownSide = side;
            } else if (eased < .5F) {
                shorten(from, fromSide, side, 1 - 2 * eased);
                shownSide = fromSide;
            } else {
                shorten(target, side, fromSide, 2 * eased - 1);
                shownSide = side;
            }
            return shown;
        }

        private void shorten(float[] rect, int own, int toward, float fraction) {
            System.arraycopy(rect, 0, shown, 0, 4);
            boolean vertical = own == Esp.LEFT || own == Esp.RIGHT;
            int start = vertical ? 1 : 0;
            int end = vertical ? 3 : 2;
            float length = (rect[end] - rect[start]) * fraction;
            if (toward == (vertical ? Esp.TOP : Esp.LEFT)) {
                shown[end] = rect[start] + length;
            } else if (toward == (vertical ? Esp.BOTTOM : Esp.RIGHT)) {
                shown[start] = rect[end] - length;
            } else {
                float middle = (rect[start] + rect[end]) / 2;
                shown[start] = middle - length / 2;
                shown[end] = middle + length / 2;
            }
        }

        float[] around(float unit) {
            return new float[]{shown[0] - unit, shown[1] - unit, shown[2] + unit, shown[3] + unit};
        }

        void reset() {
            state = -1;
        }
    }
}

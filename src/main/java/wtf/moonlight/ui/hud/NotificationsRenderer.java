package wtf.moonlight.ui.hud;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.event.Subscribe;
import wtf.moonlight.event.impl.client.ModuleToggleEvent;
import wtf.moonlight.event.impl.client.NotifyEvent;
import wtf.moonlight.module.Anchor;
import wtf.moonlight.module.impl.render.Notifications;
import wtf.moonlight.ui.clickgui.Colors;
import wtf.moonlight.ui.clickgui.Controls;
import wtf.moonlight.ui.imgui.Effects;
import wtf.moonlight.ui.imgui.Fonts;
import wtf.moonlight.ui.imgui.ImGuiManager;
import wtf.moonlight.ui.imgui.Natives;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class NotificationsRenderer implements HudElement {
    private static final float FADE = 14;
    private static final float FOLLOW = 12;
    private static final float FLASH = 10;
    private static final float FLASH_MIX = 0.4F;
    private static final float ANIMATION = 0.3F;
    private static final float SLIDE = 0.6F;
    private static final float HEIGHT = 22;
    private static final float ROUNDING = 5;
    private static final float GAP = 6;
    private static final float BAR = 2;
    private static final float BAR_HEIGHT = 21;
    private static final float ICON_X = 10;
    private static final float ICON = 14;
    private static final float TEXT_X = 31;
    private static final float TEXT_PAD = 6;
    private static final float TEXT = 10.5F;
    private static final int TOGGLE_ON = 0xE9F6;
    private static final int TOGGLE_OFF = 0xE9F5;
    private static final int SAVE = 0xE161;
    private static final int KEYBOARD = 0xE312;
    private static final int ALERT = 0xE001;
    private static final int WHITE = Colors.rgb(255, 255, 255);
    private static final int INK = Colors.rgb(28, 30, 38);
    private static final int SLATE = Colors.rgb(152, 164, 184);
    private static final int RED = Colors.rgb(213, 34, 54);
    private static final int FILL = Colors.rgba(0, 0, 0, 179);
    private static final int LIGHT_FILL = Colors.rgba(255, 255, 255, 179);
    private static final int SHADOW = Colors.rgba(0, 0, 0, 179);
    private static final int LIGHT_SHADOW = Colors.rgba(0, 0, 0, 77);
    private static final Message SAMPLE = new Message("Notifications", Tone.ENABLED, TOGGLE_ON, "Notifications");

    private final Notifications module;
    private final Queue<Message> inbox = new ConcurrentLinkedQueue<>();
    private final List<Entry> entries = new ArrayList<>();
    private float progress;
    private float width;
    private float height;
    private float unit = 1;
    private @Nullable Rect bounds;

    public NotificationsRenderer(Notifications module) {
        this.module = module;
    }

    @Override
    public Notifications module() {
        return module;
    }

    @Override
    public @Nullable Rect bounds() {
        return bounds;
    }

    @Override
    public void place(float x, float y) {
        ImGuiIO io = ImGui.getIO();
        module.anchor().drop(io.getDisplaySizeX(), io.getDisplaySizeY(), x, y, width, height, unit);
    }

    @Override
    public void frame(Style style) {
        ImGuiIO io = ImGui.getIO();
        bounds = null;
        float delta = Math.min(io.getDeltaTime(), 0.1F);
        progress = Math.clamp(progress + (module.isEnabled() ? delta : -delta) / ANIMATION, 0.0F, 1.0F);
        if (progress <= 0) {
            entries.clear();
            inbox.clear();
            return;
        }
        for (Message message; (message = inbox.poll()) != null; ) receive(message);
        sample(style.editing());
        evict();
        float k = module.scale() * style.scale() * ImGuiManager.displayScale();
        animate(delta, k);
        if (entries.isEmpty()) return;

        Anchor anchor = module.anchor();
        boolean right = anchor.right();
        boolean bottom = anchor.bottom();
        float cornerX = anchor.left(io.getDisplaySizeX(), 0, k);
        float cornerY = anchor.top(io.getDisplaySizeY(), 0, k);
        float wide = 0;
        float near = Float.MAX_VALUE;
        float far = 0;
        for (Entry entry : entries) {
            wide = Math.max(wide, entry.width);
            near = Math.min(near, entry.offset);
            far = Math.max(far, entry.offset + HEIGHT);
        }
        width = wide * k;
        height = (far - near) * k;
        unit = k;
        float minX = Math.round(right ? cornerX - width : cornerX);
        float minY = Math.round(bottom ? cornerY - far * k : cornerY + near * k);
        bounds = new Rect(minX, minY, minX + width, minY + height, ROUNDING * k);

        boolean light = style.light();
        float alpha = 1 - (float) Math.pow(1 - progress, 3);
        ImDrawList draw = ImGui.getBackgroundDrawList();
        for (Pass pass : Pass.values()) {
            for (Entry entry : entries) {
                float shown = alpha * entry.shown;
                if (shown < 0.005F) continue;
                float w = entry.width * k;
                float slide = (1 - entry.shown) * SLIDE * w;
                float x = Math.round(right ? cornerX - w + slide : cornerX - slide);
                float y = Math.round(bottom ? cornerY - (entry.offset + HEIGHT) * k : cornerY + entry.offset * k);
                paint(draw, pass, entry, x, y, w, shown, style, light, k);
            }
        }
    }

    private static void paint(ImDrawList draw, Pass pass, Entry entry, float x, float y, float w, float shown, Style style, boolean light, float k) {
        float maxX = x + w;
        float maxY = y + HEIGHT * k;
        switch (pass) {
            case BLUR -> Effects.blur(draw, x, y, maxX, maxY, ROUNDING * k, 0, shown);
            case SHADOW -> Effects.shadow(draw, x, y, maxX, maxY, ROUNDING * k, 0, fade(light ? LIGHT_SHADOW : SHADOW, shown));
            case FILL -> {
                Message message = entry.message;
                int color = switch (message.tone()) {
                    case INFO -> SLATE;
                    case ERROR -> RED;
                    case ENABLED -> style.accent();
                };
                draw.addRectFilled(x, y, maxX, maxY, fade(light ? LIGHT_FILL : FILL, shown * style.alpha()), ROUNDING * k);
                bar(draw, x, y, k, fade(Colors.mix(color, WHITE, FLASH_MIX * entry.flash), shown));
                Controls.addText(draw, Fonts.hudIcons(), ICON * Fonts.HUD_ICON_EM * k, Math.round(x + ICON_X * k),
                        Math.round(y + (HEIGHT / 2 - ICON / 2 - (Fonts.HUD_ICON_ASCENT - 1) * ICON) * k), fade(color, shown),
                        Character.toString(message.icon()));
                Controls.addText(draw, Fonts.hudRegular(), TEXT * Fonts.HUD_EM * k, Math.round(x + TEXT_X * k),
                        Math.round(y + (HEIGHT / 2 + Fonts.HUD_CAP * TEXT / 2 - Fonts.HUD_ASCENT * TEXT) * k),
                        fade(light ? INK : WHITE, shown), message.text());
            }
        }
    }

    private void receive(Message message) {
        Entry entry = entries.stream().filter(candidate -> candidate.message.key().equals(message.key())).findFirst().orElse(null);
        if (entry == null) {
            entry = new Entry(message);
            entries.add(entry);
        } else {
            entry.message = message;
            entry.leaving = false;
            entry.flash = 1;
        }
        entry.life = module.duration();
    }

    private void sample(boolean editing) {
        boolean wanted = editing && entries.stream().allMatch(entry -> entry.message == SAMPLE);
        Entry current = entries.stream().filter(entry -> entry.message == SAMPLE).findFirst().orElse(null);
        if (current != null) current.leaving = !wanted;
        else if (wanted) entries.add(new Entry(SAMPLE));
    }

    private void evict() {
        int live = 0;
        for (Entry entry : entries) {
            if (!entry.leaving && entry.message != SAMPLE) live++;
        }
        for (Entry entry : entries) {
            if (live <= module.max()) return;
            if (entry.leaving || entry.message == SAMPLE) continue;
            entry.leaving = true;
            live--;
        }
    }

    private void animate(float delta, float k) {
        float fade = 1 - (float) Math.exp(-FADE * delta);
        float follow = 1 - (float) Math.exp(-FOLLOW * delta);
        float flash = (float) Math.exp(-FLASH * delta);
        float goal = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            float target = TEXT_X + measure(entry.message.text(), k) + TEXT_PAD;
            if (entry.message != SAMPLE && !entry.leaving) {
                entry.life -= delta;
                if (entry.life <= 0) entry.leaving = true;
            }
            if (entry.placed) {
                entry.width += (target - entry.width) * follow;
                entry.offset += (goal - entry.offset) * follow;
            } else {
                entry.width = target;
                entry.offset = goal;
                entry.placed = true;
            }
            entry.shown += ((entry.leaving ? 0 : 1) - entry.shown) * fade;
            entry.flash *= flash;
            goal += HEIGHT + GAP;
        }
        entries.removeIf(entry -> entry.leaving && entry.shown < 0.01F);
    }

    private static void bar(ImDrawList draw, float x, float y, float k, int color) {
        float maxX = x + BAR * k;
        float top = y + BAR_HEIGHT * 0.1F * k;
        float solid = y + BAR_HEIGHT * 0.42F * k;
        float fall = y + BAR_HEIGHT * 0.6F * k;
        float bottom = y + BAR_HEIGHT * 0.9F * k;
        int clear = Colors.reAlpha(color, 0);
        ramp(draw, x, top, maxX, solid, clear, color);
        draw.addRectFilled(x, solid, maxX, fall, color);
        ramp(draw, x, fall, maxX, bottom, color, clear);
    }

    private static void ramp(ImDrawList draw, float minX, float minY, float maxX, float maxY, int from, int to) {
        int start = Natives.vertexCount(draw.ptr);
        draw.addRectFilled(minX, minY, maxX, maxY, WHITE);
        Natives.gradient(draw.ptr, start, minX, minY, minX, maxY, from, to);
    }

    private static float measure(String text, float k) {
        return Fonts.hudRegular().calcTextSizeAX(TEXT * Fonts.HUD_EM * k, Float.MAX_VALUE, 0, text) / k;
    }

    private static int fade(int color, float alpha) {
        return Colors.reAlpha(color, Math.round(Colors.alpha(color) * alpha));
    }

    @Subscribe
    private void onNotify(NotifyEvent event) {
        if (!module.isEnabled()) return;
        NotifyEvent.Kind kind = event.getKind();
        int icon = switch (kind) {
            case CONFIG -> SAVE;
            case BIND -> KEYBOARD;
            case ERROR -> ALERT;
        };
        inbox.add(new Message(List.of(kind, event.getText()), kind == NotifyEvent.Kind.ERROR ? Tone.ERROR : Tone.INFO, icon, event.getText()));
    }

    @Subscribe
    private void onToggle(ModuleToggleEvent event) {
        if (!module.isEnabled() || !module.toggles()) return;
        boolean on = event.isEnabled();
        String name = event.getModule().getName();
        inbox.add(new Message(event.getModule(), on ? Tone.ENABLED : Tone.INFO, on ? TOGGLE_ON : TOGGLE_OFF, name + (on ? " enabled" : " disabled")));
    }

    private enum Pass {
        BLUR,
        SHADOW,
        FILL
    }

    private enum Tone {
        INFO,
        ERROR,
        ENABLED
    }

    private record Message(Object key, Tone tone, int icon, String text) {
    }

    private static final class Entry {
        private Message message;
        private float life;
        private float shown;
        private float width;
        private float offset;
        private float flash;
        private boolean leaving;
        private boolean placed;

        private Entry(Message message) {
            this.message = message;
        }
    }
}

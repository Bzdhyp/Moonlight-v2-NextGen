package wtf.moonlight.ui.clickgui;

import static wtf.moonlight.ui.clickgui.Colors.rgb;
import static wtf.moonlight.ui.clickgui.Colors.rgba;

public final class Theme {
    public static final int ACCENT = rgb(75, 126, 255);

    public int clientIconBg;
    public int clientName;
    public int windowBg;
    public int windowBorder;
    public int main;
    public int secondary;
    public int textHighlight;
    public int iconHighlight;
    public int categoryHighlight;
    public int modulePanelBg;
    public int split;
    public int switchDisabledBg;
    public int switchKnob;
    public int buttonBg;
    public int buttonHoveredBg;
    public int buttonActiveBg;
    public int sliderBg;
    public int selectorBg;
    public int selectorElementHover;
    public int selectorElementActive;
    public int scrollbarTrack;
    public int scrollbarThumb;
    public int scrollbarThumbHover;
    public int scrollbarThumbActive;
    public int navText;
    public int navIcon;
    public int navLabel;
    public int caption;
    public int cardBorder;
    public int selectorBorder;
    public int contentBg;
    public int contentBorder;
    public int barLine;
    public float opacity = 1;

    private Theme() {
    }

    public boolean isDark() {
        return Colors.red(windowBg) < 100;
    }

    public int fade(int color) {
        return Colors.reAlpha(color, Math.round(Colors.alpha(color) * opacity));
    }

    static Theme of(boolean light, int accent, float opacity) {
        Theme t = light ? light() : dark();
        t.opacity = opacity;
        t.windowBg = t.fade(t.windowBg);
        t.modulePanelBg = t.fade(t.modulePanelBg);
        t.contentBg = t.fade(t.contentBg);
        t.contentBorder = t.fade(t.contentBorder);
        t.iconHighlight = accent;
        t.selectorElementHover = Colors.reAlpha(accent, 25);
        t.selectorElementActive = Colors.reAlpha(accent, 40);
        t.scrollbarThumbActive = accent;
        return t;
    }

    private static Theme dark() {
        Theme t = new Theme();
        t.clientIconBg = rgb(8, 27, 48);
        t.clientName = rgb(226, 229, 237);
        t.windowBg = rgba(13, 15, 22, 204);
        t.windowBorder = rgba(30, 33, 43, 0);
        t.main = rgb(216, 219, 228);
        t.secondary = rgb(150, 154, 166);
        t.textHighlight = rgb(226, 229, 237);
        t.categoryHighlight = rgba(44, 52, 72, 0);
        t.modulePanelBg = rgba(13, 14, 21, 163);
        t.split = rgb(25, 25, 33);
        t.switchDisabledBg = rgb(25, 25, 33);
        t.switchKnob = rgb(150, 154, 166);
        t.buttonBg = rgb(25, 28, 38);
        t.buttonHoveredBg = rgb(31, 38, 54);
        t.buttonActiveBg = rgb(39, 43, 54);
        t.sliderBg = rgb(35, 37, 46);
        t.selectorBg = rgb(25, 25, 33);
        t.scrollbarTrack = 0;
        t.scrollbarThumb = rgb(62, 70, 81);
        t.scrollbarThumbHover = rgb(98, 108, 121);
        t.navText = rgb(150, 154, 166);
        t.navIcon = rgb(150, 154, 166);
        t.navLabel = rgb(108, 112, 126);
        t.caption = rgb(105, 109, 123);
        t.cardBorder = rgb(28, 31, 41);
        t.selectorBorder = rgb(34, 34, 42);
        t.contentBg = rgba(13, 15, 22, 117);
        t.contentBorder = rgb(35, 37, 46);
        t.barLine = rgb(37, 42, 53);
        return t;
    }

    private static Theme light() {
        Theme t = dark();
        t.clientIconBg = rgb(200, 201, 199);
        t.clientName = rgb(40, 40, 40);
        t.secondary = rgb(100, 100, 100);
        t.windowBg = rgba(185, 188, 194, 242);
        t.windowBorder = rgb(185, 188, 194);
        t.main = rgb(55, 55, 55);
        t.textHighlight = rgb(40, 40, 40);
        t.categoryHighlight = rgba(170, 173, 180, 0);
        t.modulePanelBg = rgba(229, 229, 229, 245);
        t.split = rgb(210, 210, 210);
        t.switchDisabledBg = rgb(210, 210, 210);
        t.switchKnob = rgb(255, 255, 255);
        t.buttonBg = rgb(210, 210, 210);
        t.selectorBg = rgb(210, 210, 210);
        t.buttonHoveredBg = rgb(198, 198, 198);
        t.buttonActiveBg = rgb(188, 188, 188);
        t.sliderBg = rgb(205, 205, 205);
        t.scrollbarThumb = rgb(170, 170, 170);
        t.scrollbarThumbHover = rgb(140, 140, 140);
        t.navText = rgb(100, 100, 100);
        t.navIcon = rgb(100, 100, 100);
        t.navLabel = rgb(137, 137, 137);
        t.caption = rgb(140, 140, 140);
        t.cardBorder = rgb(214, 214, 214);
        t.selectorBorder = rgb(200, 200, 200);
        t.contentBg = rgba(220, 221, 223, 245);
        t.contentBorder = rgb(205, 205, 205);
        t.barLine = t.split;
        return t;
    }
}

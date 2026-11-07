package wtf.moonlight.ui.esp;

import imgui.ImDrawList;
import org.jspecify.annotations.Nullable;

public interface Models {
    @Nullable String name(Kind kind);

    void draw(ImDrawList draw, Kind kind, View view, @Nullable String tag, float left, float top, float right, float bottom, float scale);
}

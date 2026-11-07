package wtf.moonlight.module.impl.render;

import wtf.moonlight.module.Anchor;
import wtf.moonlight.module.Module;
import wtf.moonlight.module.Subcategory;
import wtf.moonlight.value.impl.BoolValue;
import wtf.moonlight.value.impl.NumberValue;

public final class Notifications extends Module {
    private final Anchor anchor = new Anchor(this, 3, 12, 12);
    private final BoolValue toggles = add(new BoolValue("Toggles", true));
    private final NumberValue duration = add(new NumberValue("Duration", 1, 10, 0.5, 3, "%.1f s"));
    private final NumberValue max = add(new NumberValue("Max", 1, 8, 1, 5, "%.0f"));
    private final NumberValue scale = add(new NumberValue("Scale", 0.5, 3, 0.05, 1.5, "%.2f"));

    public Notifications() {
        super("Notifications", Subcategory.OVERLAY);
    }

    public Anchor anchor() {
        return anchor;
    }

    public boolean toggles() {
        return toggles.get();
    }

    public float duration() {
        return duration.get().floatValue();
    }

    public int max() {
        return max.get().intValue();
    }

    public float scale() {
        return scale.get().floatValue();
    }
}

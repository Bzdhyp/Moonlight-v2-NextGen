package wtf.moonlight.event.impl.client;

import wtf.moonlight.event.Event;
import wtf.moonlight.module.Module;

public final class ModuleToggleEvent extends Event {
    private final Module module;
    private final boolean enabled;

    public ModuleToggleEvent(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    public Module getModule() {
        return module;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

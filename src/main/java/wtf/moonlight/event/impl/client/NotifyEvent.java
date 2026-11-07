package wtf.moonlight.event.impl.client;

import wtf.moonlight.event.Event;

public final class NotifyEvent extends Event {
    private final Kind kind;
    private final String text;

    public NotifyEvent(Kind kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public enum Kind {
        CONFIG,
        BIND,
        ERROR
    }
}

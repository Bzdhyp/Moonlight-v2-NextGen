package wtf.moonlight.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import wtf.moonlight.value.Value;

import java.util.function.BooleanSupplier;

public final class BoolValue extends Value<Boolean> {
    public BoolValue(String name, boolean value) {
        this(name, value, () -> true);
    }

    public BoolValue(String name, boolean value, BooleanSupplier visibility) {
        super(name, value, visibility);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) set(json.getAsBoolean());
    }
}

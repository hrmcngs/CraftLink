package dev.craftlink.network;

import com.google.gson.JsonObject;

/** Immutable UI preferences, safe to pass from the game thread to networking. */
public record ControllerPreferences(String layout, double sensitivity, int buttonSize) {
    public ControllerPreferences {
        if (!layout.equals("mobile") && !layout.equals("classic")) throw new IllegalArgumentException("Unknown layout");
        if (!Double.isFinite(sensitivity) || sensitivity < 0.03 || sensitivity > 0.5) throw new IllegalArgumentException("Invalid sensitivity");
        if (buttonSize < 80 || buttonSize > 140) throw new IllegalArgumentException("Invalid button size");
    }
    public String message() {
        JsonObject message = new JsonObject();
        message.addProperty("type", "preferences"); message.addProperty("layout", layout);
        message.addProperty("sensitivity", sensitivity); message.addProperty("buttonSize", buttonSize);
        return message.toString();
    }
}

package dev.craftlink.config;
import dev.craftlink.network.NetworkAddresses;
import net.minecraftforge.common.ForgeConfigSpec;
public final class LinkConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS, ADVERTISED_HOST;
    public static final ForgeConfigSpec.EnumValue<TouchLayout> TOUCH_LAYOUT;
    public static final ForgeConfigSpec.DoubleValue TOUCH_SENSITIVITY;
    public static final ForgeConfigSpec.IntValue TOUCH_BUTTON_SIZE;
    public static final ForgeConfigSpec.BooleanValue VIDEO_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ADDON_ACTIONS;
    static {
        var b = new ForgeConfigSpec.Builder();
        PORT = b.comment("HTTP/WebSocket port. Stop and restart the connection after changing settings.").defineInRange("port", 8765, 1024, 65535);
        BIND_ADDRESS = b.comment("Local IPv4 interface to listen on. 0.0.0.0 = all; 127.0.0.1 = USB adb reverse only.")
            .define("bindAddress", "0.0.0.0", v -> v instanceof String s && s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"));
        ADVERTISED_HOST = b.comment("Optional reachable IPv4 or DNS hostname shown first in the QR URL (e.g. private VPN address). Empty = automatic.")
            .define("advertisedHost", "", v -> v instanceof String s && (s.isEmpty() || NetworkAddresses.validHost(s)));
        TOUCH_LAYOUT = b.defineEnum("touchLayout", TouchLayout.TAP);
        TOUCH_SENSITIVITY = b.defineInRange("touchSensitivity", 0.15, 0.03, 0.5);
        TOUCH_BUTTON_SIZE = b.defineInRange("touchButtonSize", 100, 80, 140);
        VIDEO_ENABLED = b.comment("Allow approved phones to request Minecraft-only video frames.").define("videoEnabled", true);
        ADDON_ACTIONS = b.comment("Explicitly enabled addon action IDs. Select in Mobile Controller Settings; maximum 16.")
            .defineListAllowEmpty("addonActions", java.util.List.of(), dev.craftlink.addon.AddonActions::validId);
        SPEC = b.build();
    }
    public static ConnectionSettings launchSettings() {
        return ConnectionSettings.resolve(PORT.get(), BIND_ADDRESS.get(), ADVERTISED_HOST.get(),
            System.getProperty("craftlink.transport", "lan"), System.getProperty("craftlink.port", ""));
    }
    public static dev.craftlink.network.ControllerPreferences controllerPreferences() {
        return new dev.craftlink.network.ControllerPreferences(TOUCH_LAYOUT.get().id(), TOUCH_SENSITIVITY.get(), TOUCH_BUTTON_SIZE.get());
    }
    private LinkConfig() {}
}

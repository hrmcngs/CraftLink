package dev.craftlink.config;
import dev.craftlink.network.NetworkAddresses;
import net.minecraftforge.common.ForgeConfigSpec;
public final class LinkConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS, ADVERTISED_HOST;
    static {
        var b = new ForgeConfigSpec.Builder();
        PORT = b.comment("HTTP/WebSocket port. Stop and restart the connection after changing settings.").defineInRange("port", 8765, 1024, 65535);
        BIND_ADDRESS = b.comment("Local IPv4 interface to listen on. 0.0.0.0 = all; 127.0.0.1 = USB adb reverse only.")
            .define("bindAddress", "0.0.0.0", v -> v instanceof String s && s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"));
        ADVERTISED_HOST = b.comment("Optional reachable IPv4 or DNS hostname shown first in the QR URL (e.g. private VPN address). Empty = automatic.")
            .define("advertisedHost", "", v -> v instanceof String s && (s.isEmpty() || NetworkAddresses.validHost(s)));
        SPEC = b.build();
    }
    private LinkConfig() {}
}

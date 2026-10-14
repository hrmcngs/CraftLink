package dev.craftlink.config;
import net.minecraftforge.common.ForgeConfigSpec;
public final class LinkConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PORT;
    static { var b = new ForgeConfigSpec.Builder(); PORT = b.comment("LAN HTTP/WebSocket port. Restart the connection after changing.").defineInRange("port", 8765, 1024, 65535); SPEC = b.build(); }
    private LinkConfig() {}
}

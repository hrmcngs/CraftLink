package dev.craftlink;

import dev.craftlink.config.LinkConfig;
import dev.craftlink.gui.LinkScreen;
import dev.craftlink.input.InputBridge;
import dev.craftlink.network.LinkServer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

@Mod("craftlink")
public final class CraftLink {
    public CraftLink() {
        var context = FMLJavaModLoadingContext.get();
        context.registerConfig(ModConfig.Type.CLIENT, LinkConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) Client.init();
    }
    public static final class Client {
        public static final LinkServer SERVER = new LinkServer();
        public static final KeyMapping OPEN = new KeyMapping("key.craftlink.open", GLFW.GLFW_KEY_F8, "key.categories.craftlink");
        public static final dev.craftlink.video.VideoStreamer VIDEO = new dev.craftlink.video.VideoStreamer(SERVER);
        public static final InputBridge INPUT = new InputBridge(SERVER.input, VIDEO);
        private static void init() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(Client::register);
            MinecraftForge.EVENT_BUS.register(Client.class);
            MinecraftForge.EVENT_BUS.register(dev.craftlink.gui.ControlsIntegration.class);
            Runtime.getRuntime().addShutdownHook(new Thread(SERVER::shutdown, "CraftLink shutdown"));
        }
        @SubscribeEvent public static void shutdown(GameShuttingDownEvent e) { dev.craftlink.addon.AddonActions.releaseAll(); VIDEO.close(); SERVER.shutdown(); }
        @SubscribeEvent public static void render(TickEvent.RenderTickEvent e) { if (e.phase == TickEvent.Phase.END) VIDEO.capture(Minecraft.getInstance()); }
        private static void register(RegisterKeyMappingsEvent e) { e.register(OPEN); }
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getInstance();
            SERVER.setControllerPreferences(LinkConfig.controllerPreferences());
            SERVER.setVideoAllowed(LinkConfig.VIDEO_ENABLED.get());
            while (OPEN.consumeClick()) mc.setScreen(new LinkScreen(mc.screen));
            SERVER.setAddonActions(dev.craftlink.addon.AddonActions.enabled(mc));
            INPUT.tick(mc);
        }
    }
}

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
        dev.craftlink.classroom.ClassroomEntities.register(context.getModEventBus());
        if (FMLEnvironment.dist == Dist.CLIENT) Client.init();
    }
    public static final class Client {
        public static final LinkServer SERVER = new LinkServer();
        public static final KeyMapping OPEN = new KeyMapping("key.craftlink.open", GLFW.GLFW_KEY_F8, "key.categories.craftlink");
        public static final KeyMapping CLASSROOM_PAUSE = new KeyMapping("key.craftlink.classroomPause", GLFW.GLFW_KEY_F9, "key.categories.craftlink");
        public static final dev.craftlink.video.VideoStreamer VIDEO = new dev.craftlink.video.VideoStreamer(SERVER);
        public static final InputBridge INPUT = new InputBridge(SERVER.input, VIDEO);
        public static final dev.craftlink.classroom.ClassroomServer CLASSROOM = new dev.craftlink.classroom.ClassroomServer();
        public static final dev.craftlink.classroom.ClassroomBots BOTS = new dev.craftlink.classroom.ClassroomBots();
        public static final dev.craftlink.video.BotVideoStreamer BOT_VIDEO = new dev.craftlink.video.BotVideoStreamer(CLASSROOM, BOTS);
        private static dev.craftlink.classroom.ClassroomRoom activeRoom;
        private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> classroomDimension;
        private static Boolean previousPauseOnLostFocus;
        private static boolean schoolStarted, worldReady;
        private static final dev.craftlink.input.WorldStartGate WORLD_START=new dev.craftlink.input.WorldStartGate();
        private static void init() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(Client::register);
            MinecraftForge.EVENT_BUS.register(Client.class);
            MinecraftForge.EVENT_BUS.register(dev.craftlink.gui.ControlsIntegration.class);
            MinecraftForge.EVENT_BUS.register(BOTS);
            Runtime.getRuntime().addShutdownHook(new Thread(SERVER::shutdown, "CraftLink shutdown"));
            Runtime.getRuntime().addShutdownHook(new Thread(CLASSROOM::shutdown, "CraftLink classroom shutdown"));
        }
        public static void stopClassroom() {
            CLASSROOM.stop(); BOTS.stop(); activeRoom = null; classroomDimension = null;
            if (previousPauseOnLostFocus != null) {
                var options = Minecraft.getInstance().options;
                options.pauseOnLostFocus = previousPauseOnLostFocus; previousPauseOnLostFocus = null; options.save();
            }
        }
        @SubscribeEvent public static void shutdown(GameShuttingDownEvent e) { stopClassroom(); CLASSROOM.shutdown(); dev.craftlink.addon.AddonActions.releaseAll(); BOT_VIDEO.close(); VIDEO.close(); SERVER.shutdown(); }
        @SubscribeEvent public static void render(TickEvent.RenderTickEvent e) { if (e.phase == TickEvent.Phase.END) { VIDEO.capture(Minecraft.getInstance()); } }
        @SubscribeEvent public static void renderWorld(net.minecraftforge.client.event.RenderLevelStageEvent e) {
            if(e.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL || !worldReady || BOT_VIDEO.rendering())return;
            try { BOT_VIDEO.capture(Minecraft.getInstance(),e.getPartialTick()); }
            catch(RuntimeException failure){BOT_VIDEO.suspend(failure);}
        }
        private static void register(RegisterKeyMappingsEvent e) { e.register(OPEN); e.register(CLASSROOM_PAUSE); }
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getInstance();
            worldReady=WORLD_START.tick(mc.level,mc.player!=null && mc.getOverlay()==null && !(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen));
            if(!LinkConfig.SPEC.isLoaded())return;
            while (CLASSROOM_PAUSE.consumeClick()) { var current = CLASSROOM.room(); if (current != null) current.pause(true); }
            var room = CLASSROOM.room();
            if (room != null) {
                if (mc.getSingleplayerServer() == null || mc.player == null || room.expired()
                    || room == activeRoom && !mc.player.level().dimension().equals(classroomDimension)) stopClassroom();
                else if (room != activeRoom && worldReady) {
                    activeRoom = room;
                    classroomDimension = mc.player.level().dimension();
                    previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
                    mc.options.pauseOnLostFocus = false;
                    try { room.catalog(dev.craftlink.classroom.CreativeCatalog.build(mc)); } catch (Exception ignored) { room.catalog(null); }
                    BOTS.start(mc.getSingleplayerServer(),room,mc.player.level().dimension(),mc.player.blockPosition(),mc.player.getYRot());
                }
            }
            String schoolMode = System.getProperty("craftlink.schoolMode", "");
            if (schoolMode.equals("classroom") || schoolMode.equals("controller")) {
                if (mc.player == null) {
                    if (schoolStarted && schoolMode.equals("controller")) SERVER.stop();
                    schoolStarted = false;
                } else if (!schoolStarted && worldReady) {
                    schoolStarted = true;
                    var settings = LinkConfig.launchSettings();
                    if (schoolMode.equals("classroom")) {
                        if (mc.getSingleplayerServer() != null) {
                            CLASSROOM.start(LinkConfig.CLASSROOM_PORT.get(), settings.bindAddress(), settings.advertisedHost(), LinkConfig.externalUrl(true));
                            mc.setScreen(new dev.craftlink.gui.ClassroomScreen(mc.screen));
                        } else mc.setScreen(new LinkScreen(mc.screen));
                    } else {
                        SERVER.start(settings.port(), settings.bindAddress(), settings.advertisedHost(), LinkConfig.externalUrl(false));
                        mc.setScreen(new LinkScreen(mc.screen));
                    }
                }
            }
            SERVER.setControllerPreferences(LinkConfig.controllerPreferences());

            SERVER.setVideoAllowed(LinkConfig.VIDEO_ENABLED.get());
            while (OPEN.consumeClick()) mc.setScreen(new LinkScreen(mc.screen));
            SERVER.setAddonActions(dev.craftlink.addon.AddonActions.enabled(mc));
            INPUT.tick(mc);
        }
    }
}

package dev.craftlink.classroom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.craftlink.classroom.ClassroomBot;
import dev.craftlink.classroom.ClassroomEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla player geometry, skin overlays, limb animation and synchronized arm swing. */
@Mod.EventBusSubscriber(modid = "craftlink", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClassroomBotRenderer extends MobRenderer<ClassroomBot, PlayerModel<ClassroomBot>> {
    private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny"};
    private static final ResourceLocation[] TEXTURES = java.util.Arrays.stream(SKINS)
        .map(name -> new ResourceLocation("minecraft", "textures/entity/player/wide/" + name + ".png")).toArray(ResourceLocation[]::new);
    public ClassroomBotRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), .5f);
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this,context.getItemInHandRenderer()));
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ClassroomEntities.BOT.get(), ClassroomBotRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(ClassroomBot bot) {
        return TEXTURES[Math.floorMod(bot.participantNumber() - 1, TEXTURES.length)];
    }
    @Override public void render(ClassroomBot bot, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model.crouching = bot.isCrouching();
        super.render(bot, yaw, partialTick, pose, buffers, light);
    }
    @Override protected boolean shouldShowName(ClassroomBot bot) { return bot.hasCustomName() && bot.isCustomNameVisible(); }
}

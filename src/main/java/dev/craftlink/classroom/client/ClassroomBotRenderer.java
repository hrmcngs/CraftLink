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
    private static final ResourceLocation[] TEXTURES = dev.craftlink.classroom.ParticipantProfile.SKINS.stream()
        .map(name -> new ResourceLocation("minecraft", "textures/entity/player/wide/" + name + ".png")).toArray(ResourceLocation[]::new);
    public ClassroomBotRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), .5f);
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),context.getModelManager()));
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this,context.getItemInHandRenderer()));
        addLayer(new net.minecraft.client.renderer.entity.layers.ElytraLayer<>(this,context.getModelSet()));
        addLayer(new net.minecraft.client.renderer.entity.layers.CustomHeadLayer<>(this,context.getModelSet(),context.getItemInHandRenderer()));
        addAccessoryLayer();
    }
    @SuppressWarnings("unchecked")
    private void addAccessoryLayer() {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("curios"))return;
        try {
            var type=Class.forName("top.theillusivec4.curios.client.render.CuriosLayer");
            var layer=type.getConstructor(net.minecraft.client.renderer.entity.RenderLayerParent.class).newInstance(this);
            addLayer((net.minecraft.client.renderer.entity.layers.RenderLayer<ClassroomBot,PlayerModel<ClassroomBot>>)layer);
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register Curios renderer for BOT",e);
        }
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ClassroomEntities.BOT.get(), ClassroomBotRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(ClassroomBot bot) {
        return TEXTURES[Math.floorMod(bot.skinIndex(), TEXTURES.length)];
    }
    @Override public void render(ClassroomBot bot, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model.crouching = bot.isCrouching();
        super.render(bot, yaw, partialTick, pose, buffers, light);
    }
    @Override protected boolean shouldShowName(ClassroomBot bot) { return bot.hasCustomName() && bot.isCustomNameVisible(); }
}

package dev.craftlink.video;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.craftlink.classroom.ClassroomBot;
import dev.craftlink.classroom.client.ClassroomBotRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.util.Mth;

/** BOT-owned first-person foreground; never substitutes or modifies Minecraft's LocalPlayer. */
public final class BotFirstPersonRenderer {
    private PlayerModel<ClassroomBot> model;
    private final MultiBufferSource.BufferSource buffers=MultiBufferSource.immediate(new BufferBuilder(4096));
    public void render(Minecraft mc,ClassroomBot bot,float partial){
        int light=mc.getEntityRenderDispatcher().getPackedLightCoords(bot,partial);
        float swing=bot.getAttackAnim(partial), root=Mth.sqrt(swing);
        PoseStack pose=new PoseStack();
        var stack=bot.getMainHandItem();
        if(stack.isEmpty()){
            if(model==null)model=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
            pose.translate(.64-.3*Mth.sin(root*(float)Math.PI),-.6+.4*Mth.sin(root*(float)Math.PI*2),-.72-.4*Mth.sin(swing*(float)Math.PI));
            pose.mulPose(Axis.YP.rotationDegrees(45+70*Mth.sin(root*(float)Math.PI)));
            pose.mulPose(Axis.ZP.rotationDegrees(-20*Mth.sin(swing*swing*(float)Math.PI)));
            pose.translate(-1,3.6,3.5);pose.mulPose(Axis.ZP.rotationDegrees(120));pose.mulPose(Axis.XP.rotationDegrees(200));pose.mulPose(Axis.YP.rotationDegrees(-135));pose.translate(5.6,0,0);
            if(mc.getEntityRenderDispatcher().getRenderer(bot) instanceof ClassroomBotRenderer renderer){
                var texture=renderer.getTextureLocation(bot);
                model.crouching=false;model.attackTime=0;model.swimAmount=0;model.setupAnim(bot,0,0,0,0,0);
                model.rightArm.xRot=0;model.rightSleeve.xRot=0;
                model.rightArm.render(pose,buffers.getBuffer(RenderType.entitySolid(texture)),light,OverlayTexture.NO_OVERLAY);
                model.rightSleeve.render(pose,buffers.getBuffer(RenderType.entityTranslucent(texture)),light,OverlayTexture.NO_OVERLAY);
            }
        }else{
            pose.translate(.56-.4*Mth.sin(root*(float)Math.PI),-.52+.2*Mth.sin(root*(float)Math.PI*2),-.72-.2*Mth.sin(swing*(float)Math.PI));
            pose.mulPose(Axis.YP.rotationDegrees(45-20*Mth.sin(swing*swing*(float)Math.PI)));
            pose.mulPose(Axis.ZP.rotationDegrees(-20*Mth.sin(root*(float)Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees(-80*Mth.sin(root*(float)Math.PI)));
            mc.getItemRenderer().renderStatic(bot,stack,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,false,pose,buffers,mc.level,light,OverlayTexture.NO_OVERLAY,bot.getId());
        }
        buffers.endBatch();
    }
}

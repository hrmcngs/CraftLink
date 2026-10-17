package dev.craftlink.mixin;

import dev.craftlink.CraftLink.Client;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auxiliary views must not draw terrain from another camera's asynchronous visibility graph. */
@Mixin(LevelRenderer.class)
abstract class BotTerrainMixin {
    @Shadow private Future<?> lastFullRenderChunkUpdate;
    @Shadow private boolean needsFullRenderChunkUpdate;
    @Shadow @Final private AtomicBoolean needsFrustumUpdate;

    private void craftlink$awaitTerrain(){
        if(lastFullRenderChunkUpdate==null)return;
        try{lastFullRenderChunkUpdate.get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("BOT terrain update interrupted",e);}
        catch(java.util.concurrent.ExecutionException e){throw new IllegalStateException("BOT terrain update failed",e.getCause());}
    }
    @Inject(method="setupRender",at=@At("HEAD"))
    private void craftlink$refresh(Camera camera,Frustum frustum,boolean captured,boolean spectator,CallbackInfo ci){
        if(!Client.BOT_VIDEO.rendering())return;
        craftlink$awaitTerrain();needsFullRenderChunkUpdate=true;needsFrustumUpdate.set(true);
    }
    @Inject(method="setupRender",at=@At(value="INVOKE",target="Ljava/util/concurrent/atomic/AtomicReference;get()Ljava/lang/Object;"))
    private void craftlink$ready(Camera camera,Frustum frustum,boolean captured,boolean spectator,CallbackInfo ci){
        if(Client.BOT_VIDEO.rendering())craftlink$awaitTerrain();
    }
    @Inject(method="setupRender",at=@At("RETURN"))
    private void craftlink$restoreHost(Camera camera,Frustum frustum,boolean captured,boolean spectator,CallbackInfo ci){
        if(Client.BOT_VIDEO.rendering()){needsFullRenderChunkUpdate=true;needsFrustumUpdate.set(true);}
    }
}

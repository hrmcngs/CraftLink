package dev.craftlink.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.level.GameType;

@Mixin(ServerPlayerGameMode.class)
public interface BlockActionAccessor {
    @Accessor("isDestroyingBlock") boolean craftlink$destroying();
    @Accessor("hasDelayedDestroy") void craftlink$delayed(boolean value);
    @Invoker("setGameModeForPlayer") void craftlink$mode(GameType value,GameType previous);
}

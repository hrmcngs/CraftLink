package dev.craftlink.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Invoke normal game actions directly, keeping reach/cooldowns/Forge hooks intact. */
@Mixin(Minecraft.class)
public interface MinecraftActions {
    @Invoker("startAttack") boolean craftlink$startAttack();
    @Invoker("startUseItem") void craftlink$startUseItem();
    @Accessor("rightClickDelay") int craftlink$useDelay();
}

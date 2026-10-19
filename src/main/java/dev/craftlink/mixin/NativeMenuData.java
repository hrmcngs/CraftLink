package dev.craftlink.mixin;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractContainerMenu.class)
public interface NativeMenuData {
 @Accessor("dataSlots") List<DataSlot> craftlink$data();
}

package dev.craftlink.classroom;

import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;

/** Copy Curios state across the server/client preview boundary without sharing live stacks. */
public final class AccessoryAppearance {
    private static Object handler(LivingEntity entity) throws ReflectiveOperationException {
        var api=Class.forName("top.theillusivec4.curios.api.CuriosApi");
        var optional=(net.minecraftforge.common.util.LazyOptional<?>)api.getMethod("getCuriosInventory",LivingEntity.class).invoke(null,entity);
        return optional.resolve().orElse(null);
    }
    public static Tag capture(LivingEntity entity) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("curios"))return null;
        try {
            var handler=handler(entity);if(handler==null)return null;
            var type=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            return ((Tag)type.getMethod("writeTag").invoke(handler)).copy();
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot snapshot Curios appearance",e); }
    }
    public static void apply(LivingEntity entity,Tag snapshot) {
        if(snapshot==null)return;
        try {
            var handler=handler(entity);if(handler==null)throw new IllegalStateException("Preview has no Curios inventory");
            var type=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            type.getMethod("readTag",Tag.class).invoke(handler,snapshot.copy());
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot restore Curios appearance",e); }
    }
    private AccessoryAppearance() {}
}

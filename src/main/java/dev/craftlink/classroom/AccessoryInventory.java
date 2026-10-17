package dev.craftlink.classroom;

import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Optional Curios bridge: no class loading dependency when Curios is absent. */
final class AccessoryInventory {
    record Slot(String name,IItemHandlerModifiable inventory,int index) {}
    static List<Slot> slots(LivingEntity entity){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("curios"))return List.of();
        try {
            var api=Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var optional=(net.minecraftforge.common.util.LazyOptional<?>)api.getMethod("getCuriosInventory",LivingEntity.class).invoke(null,entity);
            var handler=optional.resolve().orElse(null);if(handler==null)return List.of();
            var type=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            var stacksType=Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            var map=(Map<?,?>)type.getMethod("getCurios").invoke(handler);
            if(map.isEmpty()&&!entity.level().isClientSide()){
                // BOTs inherit the installed modpack's player slots, including custom slot types.
                var definitions=(Map<?,?>)api.getMethod("getPlayerSlots",net.minecraft.world.level.Level.class).invoke(null,entity.level());
                var slotType=Class.forName("top.theillusivec4.curios.api.type.ISlotType");
                var dropType=Class.forName("top.theillusivec4.curios.api.type.capability.ICurio$DropRule");
                var constructor=Class.forName("top.theillusivec4.curios.common.inventory.CurioStacksHandler").getConstructor(type,String.class,int.class,boolean.class,boolean.class,boolean.class,dropType);
                var initialized=new LinkedHashMap<String,Object>();
                for(var definition:definitions.entrySet()){
                    Object value=definition.getValue();int size=(Integer)slotType.getMethod("getSize").invoke(value);
                    initialized.put(definition.getKey().toString(),constructor.newInstance(handler,definition.getKey().toString(),size,true,false,true,slotType.getMethod("getDropRule").invoke(value)));
                }
                type.getMethod("setCurios",Map.class).invoke(handler,initialized);map=initialized;
            }
            var result=new ArrayList<Slot>();
            for(var entry:map.entrySet()){
                var stacks=(IItemHandlerModifiable)stacksType.getMethod("getStacks").invoke(entry.getValue());
                for(int i=0;i<stacks.getSlots()&&result.size()<128;i++)result.add(new Slot(entry.getKey()+" "+(i+1),stacks,i));
            }
            return List.copyOf(result);
        }catch(ReflectiveOperationException|ClassCastException e){
            if(!warned){warned=true;org.slf4j.LoggerFactory.getLogger(AccessoryInventory.class).warn("Cannot access Curios inventory",e);}return List.of();
        }
    }
    private static boolean warned;
}

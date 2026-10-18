package dev.craftlink.classroom;

import java.lang.reflect.Proxy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Optional bridge to the mod's own backpack lookup, slot validation and storage menu. */
final class SophisticatedBackpacks {
    static boolean available(){return net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedbackpacks");}
    static String open(ClassroomBot bot,ServerPlayer player){
        if(!available())return "Sophisticated Backpacksが導入されていません。";
        try {
            // The action player uses the BOT's actual Curios handlers, not duplicate backpacks.
            if(net.minecraftforge.fml.ModList.get().isLoaded("curios")){
                AccessoryInventory.slots(bot);
                var api=Class.forName("top.theillusivec4.curios.api.CuriosApi");
                var method=api.getMethod("getCuriosInventory",net.minecraft.world.entity.LivingEntity.class);
                var source=((net.minecraftforge.common.util.LazyOptional<?>)method.invoke(null,bot)).resolve().orElse(null);
                var target=((net.minecraftforge.common.util.LazyOptional<?>)method.invoke(null,player)).resolve().orElse(null);
                if(source!=null&&target!=null){
                    var handler=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                    handler.getMethod("setCurios",java.util.Map.class).invoke(target,handler.getMethod("getCurios").invoke(source));
                }
            }
            var providerType=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider");
            var consumerType=Class.forName(providerType.getName()+"$BackpackInventorySlotConsumer");
            var contextType=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext");
            var itemContext=Class.forName(contextType.getName()+"$Item").getConstructor(String.class,String.class,int.class);
            var menuConstructor=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer").getConstructor(int.class,Player.class,contextType);
            boolean[] opened={false};
            var callback=Proxy.newProxyInstance(consumerType.getClassLoader(),new Class<?>[]{consumerType},(proxy,method,args)->{
                if(!method.getName().equals("accept"))return switch(method.getName()){
                    case "toString" -> "CraftLink backpack lookup";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    default -> null;
                };
                var stack=(ItemStack)args[0];
                var context=itemContext.newInstance(args[1],args[2],args[3]);
                opened[0]=player.openMenu(new SimpleMenuProvider((id,inventory,owner)->{
                    try{return (AbstractContainerMenu)menuConstructor.newInstance(id,owner,context);}
                    catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot create backpack menu",e);}
                },stack.getHoverName())).isPresent();
                return opened[0];
            });
            providerType.getMethod("runOnBackpacks",Player.class,consumerType).invoke(providerType.getMethod("get").invoke(null),player,callback);
            return opened[0]?null:"開けるバックパックがありません。所持品かアクセサリー欄に装備してください。";
        }catch(ReflectiveOperationException|RuntimeException e){
            com.mojang.logging.LogUtils.getLogger().warn("CraftLink cannot open Sophisticated Backpack",e);
            return "バックパックを開けませんでした。MODのバージョンとPC側のログを確認してください。";
        }
    }
    private SophisticatedBackpacks(){}
}

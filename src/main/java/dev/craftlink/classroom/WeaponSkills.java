package dev.craftlink.classroom;

import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Optional server bridge; skills always run as the requesting BOT's action player. */
final class WeaponSkills {
    private static Method weapon,normal,charged,sheathe;
    private static boolean initialized;
    static boolean available(){
        if(!initialized){initialized=true;
            if(net.minecraftforge.fml.ModList.get().isLoaded("the_four_primitives_and_weapons"))try{
                var type=Class.forName("the_four_primitives_and_weapons.events.ChargedAttackHandler");
                weapon=type.getMethod("isWeapon",ItemStack.class);normal=type.getMethod("performNormalAttack",Player.class);charged=type.getMethod("performChargedAttack",Player.class,float.class);
                sheathe=Class.forName("the_four_primitives_and_weapons.network.RMessage").getMethod("pressAction",Player.class,int.class,int.class);
            }catch(ReflectiveOperationException e){com.mojang.logging.LogUtils.getLogger().warn("CraftLink MWA skills unavailable",e);normal=null;}
        }
        return normal!=null;
    }
    static boolean attack(Player player,float charge){
        if(!available())return false;
        try{
            if(!(Boolean)weapon.invoke(null,player.getMainHandItem()))return false;
            if(charge>=0)charged.invoke(null,player,charge);else normal.invoke(null,player);
            return true;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("MWA BOT attack failed",e);}
    }
    static boolean supports(ItemStack stack){
        if(!available())return false;
        try{return (Boolean)weapon.invoke(null,stack);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot check MWA weapon",e);}
    }
    static void sheathe(Player player){
        if(!available())return;
        try{sheathe.invoke(null,player,0,0);}catch(ReflectiveOperationException e){throw new IllegalStateException("MWA BOT sheath action failed",e);}
    }
}

package dev.craftlink.classroom;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Optional MAW server bridge. Every value belongs to this participant's action player. */
final class MawHud {
    private static final String PREFIX="the_four_primitives_and_weapons.";
    private static final Map<String,Method> METHODS=new HashMap<>();
    private static final java.util.Set<String> FAILURES=new java.util.HashSet<>();
    private ItemStack editingWeapon=ItemStack.EMPTY;
    private static boolean loaded(){return net.minecraftforge.fml.ModList.get().isLoaded("the_four_primitives_and_weapons");}
    private static Method method(String name,String method,Class<?>... parameters)throws ReflectiveOperationException{
        String key=name+"#"+method+java.util.Arrays.toString(parameters);
        Method cached=METHODS.get(key);
        if(cached==null){cached=Class.forName(PREFIX+name).getMethod(method,parameters);METHODS.put(key,cached);}
        return cached;
    }
    private static void failed(String part,ReflectiveOperationException error){
        if(FAILURES.add(part))com.mojang.logging.LogUtils.getLogger().warn("CraftLink MAW {} unavailable",part,error);
    }
    static void appendStatus(JsonObject status,ClassroomBot bot,Player player){
        if(!loaded())return;
        status.addProperty("soulFire",bot.isOnFire()&&bot.getPersistentData().contains("TfpwSoulFireUntil")&&bot.level().getGameTime()<=bot.getPersistentData().getLong("TfpwSoulFireUntil"));
        var hud=new JsonObject();
        if(!FAILURES.contains("mana"))try{
            double mana=((Number)method("mana.ManaHelper","getMana",Player.class).invoke(null,player)).doubleValue();
            double max=((Number)method("mana.ManaHelper","maxMana",Player.class).invoke(null,player)).doubleValue();
            if(Double.isFinite(mana)&&Double.isFinite(max)&&max>0){hud.addProperty("mana",Math.max(0,Math.min(max,mana)));hud.addProperty("maxMana",max);}
        }catch(ReflectiveOperationException e){failed("mana",e);}
        if(!FAILURES.contains("shield"))try{
            Class<?> shield=Class.forName(PREFIX+"item.ParryShieldItem");
            if(player.isUsingItem()&&shield.isInstance(player.getUseItem().getItem())){
                int maximum=shield.getField("MAX_CHARGE_TICKS").getInt(null),minimum=shield.getField("MIN_CHARGE_TICKS").getInt(null);
                if(maximum>0){hud.addProperty("shieldCharge",Math.min(1,player.getTicksUsingItem()/(double)maximum));hud.addProperty("shieldThreshold",minimum/(double)maximum);}
            }
        }catch(ReflectiveOperationException e){failed("shield",e);}
        if(!FAILURES.contains("text"))try{
            Object text=method("procedures.DddgemuNeiniobareiwoBiaoShiProcedure","execute",net.minecraft.world.entity.Entity.class).invoke(null,player);
            if(text instanceof String value&&!value.isBlank())hud.addProperty("text",value.substring(0,Math.min(160,value.length())));
        }catch(ReflectiveOperationException e){failed("text",e);}
        status.add("maw",hud);
    }
    private static Class<?> slotClass()throws ClassNotFoundException{return Class.forName(PREFIX+"skill.PlayerSkillData$AttackSlot");}
    private static List<?> motions(Object slot,ItemStack weapon)throws ReflectiveOperationException{
        return (List<?>)method("skill.SkillRegistry","getAvailableMotionsForWeapon",slotClass(),ItemStack.class).invoke(null,slot,weapon);
    }
    JsonObject skills(Player player){
        var message=new JsonObject();message.addProperty("type","mawSkills");var slots=new JsonArray();message.add("slots",slots);
        if(!loaded()){message.addProperty("error","MAWが導入されていません。");return message;}
        editingWeapon=player.getMainHandItem().copy();message.addProperty("weapon",editingWeapon.isEmpty()?"手持ちなし":editingWeapon.getHoverName().getString());
        if(editingWeapon.isEmpty()){message.addProperty("error","設定する武器をメインハンドに持ってください。");return message;}
        try{
            Object storage=method("skill.PlayerSkillData","getSkillData",Player.class).invoke(null,player);
            Object[] attackSlots=slotClass().getEnumConstants();
            for(int i=0;i<attackSlots.length;i++){
                Object slot=attackSlots[i];var choices=new JsonArray();
                for(Object motion:motions(slot,editingWeapon)){
                    var choice=new JsonObject();choice.addProperty("id",(String)motion.getClass().getMethod("getId").invoke(motion));
                    choice.addProperty("name",Component.translatable((String)motion.getClass().getMethod("translationKey").invoke(motion)).getString());
                    choice.addProperty("description",Component.translatable((String)motion.getClass().getMethod("descriptionTranslationKey").invoke(motion)).getString());choices.add(choice);
                    if(choices.size()>=256)break;
                }
                if(choices.isEmpty())continue;
                var entry=new JsonObject();entry.addProperty("slot",i);entry.addProperty("name",(String)slot.getClass().getMethod("getDisplayName").invoke(slot));
                entry.addProperty("selected",(String)storage.getClass().getMethod("getMotionForWeapon",slotClass(),ItemStack.class).invoke(storage,slot,editingWeapon));entry.add("choices",choices);slots.add(entry);
            }
        }catch(ReflectiveOperationException e){failed("skills",e);message.addProperty("error","このMAWバージョンの技選択には対応できませんでした。");}
        return message;
    }
    boolean select(Player player,int index,String motionId){
        if(!loaded()||editingWeapon.isEmpty()||!ItemStack.isSameItemSameTags(editingWeapon,player.getMainHandItem()))return false;
        try{
            Object[] slots=slotClass().getEnumConstants();if(index<0||index>=slots.length)return false;
            boolean allowed=false;
            for(Object motion:motions(slots[index],player.getMainHandItem()))if(motionId.equals(motion.getClass().getMethod("getId").invoke(motion))){allowed=true;break;}
            if(!allowed)return false;
            method("skill.WeaponSkillNBT","setMotion",ItemStack.class,slotClass(),String.class).invoke(null,player.getMainHandItem(),slots[index],motionId);
            editingWeapon=player.getMainHandItem().copy();return true;
        }catch(ReflectiveOperationException e){failed("skills",e);return false;}
    }
}

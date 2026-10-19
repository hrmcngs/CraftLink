package dev.craftlink.video;

import com.google.gson.JsonObject;
import dev.craftlink.classroom.ClassroomRoom;
import dev.craftlink.mixin.NativeMenuAccess;
import dev.craftlink.mixin.NativeMenuData;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Snapshots cross threads; live server menus and the PC player's inventory never do. */
public final class NativeMenuImages {
 private record Slot(int x,int y,ItemStack item){}
 private record Job(ClassroomRoom room,UUID peer,int id,long revision,String type,String title,List<Slot> slots,List<Integer> data){}
 private static final Map<UUID,Job> pending=new LinkedHashMap<>();
 private record Hud(ClassroomRoom room,UUID peer){}
 private static final Map<UUID,Hud> hudPending=new LinkedHashMap<>();
 private static final Set<UUID> hudSent=new HashSet<>();
 private static String hudSheet;
 private static final Map<String,String> overlays=new HashMap<>();
 public static synchronized void request(ClassroomRoom room,UUID peer,AbstractContainerMenu menu,String title,long revision){
  // InventoryScreen and custom menu constructors need participant-specific client context.
  MenuType<?> type;try{type=menu.getType();}catch(UnsupportedOperationException ignored){return;}
  var id=BuiltInRegistries.MENU.getKey(type);if(id==null||!id.getNamespace().equals("minecraft")||menu.slots.size()>256)return;
  if(Set.of("merchant","beacon","enchantment","lectern","loom","stonecutter").contains(id.getPath()))return;
  if(pending.size()>=8&&!pending.containsKey(peer))return;
  var slots=menu.slots.stream().map(slot->new Slot(slot.x,slot.y,slot.getItem().copy())).toList();
  var data=((NativeMenuData)menu).craftlink$data().stream().map(net.minecraft.world.inventory.DataSlot::get).toList();
  pending.put(peer,new Job(room,peer,menu.containerId,revision,id.toString(),title,slots,data));
 }
 public static synchronized void hud(ClassroomRoom room,UUID peer){if(!hudSent.contains(peer)&&hudPending.size()<8)hudPending.put(peer,new Hud(room,peer));}
 @SuppressWarnings({"rawtypes","unchecked"})
 private static void render(Minecraft mc,Job job)throws Exception{
  var inventory=new Inventory(mc.player); // independent slots; never write mc.player.getInventory().
  MenuType type=BuiltInRegistries.MENU.get(ResourceLocation.tryParse(job.type));if(type==null)return;
  AbstractContainerMenu menu=type.create(job.id,inventory);if(menu.slots.size()!=job.slots.size())return;
  for(int i=0;i<menu.slots.size();i++){
   var source=job.slots.get(i);var target=menu.slots.get(i);
   if(source.x!=target.x||source.y!=target.y)return;
   target.set(source.item.copy());
  }
  for(int i=0;i<job.data.size();i++)menu.setData(i,job.data.get(i));
  var title=Component.literal(job.title);
  var factory=MenuScreens.getScreenFactory(type,mc,job.id,title);if(factory.isEmpty())return;
  var created=((MenuScreens.ScreenConstructor)factory.get()).create(menu,inventory,title);
  if(!(created instanceof AbstractContainerScreen<?> screen))return;
  int width=screen.getXSize(),height=screen.getYSize();if(width<16||height<16||width>512||height>512)return;
  screen.init(mc,width,height);
  var image=ItemIcons.renderImage(mc,width,height,width*2,height*2,graphics->{
   var access=(NativeMenuAccess)screen;
   graphics.pose().pushPose();graphics.pose().translate(-screen.getGuiLeft(),-screen.getGuiTop(),0);
   access.craftlink$background(graphics,0,-1000,-1000);graphics.pose().popPose();
   access.craftlink$labels(graphics,-1000,-1000);
   for(var slot:menu.slots)if(slot.hasItem()){graphics.renderItem(slot.getItem(),slot.x,slot.y);graphics.renderItemDecorations(mc.font,slot.getItem(),slot.x,slot.y);}
  });
  var message=new JsonObject();message.addProperty("type","nativeMenu");message.addProperty("id",job.id);message.addProperty("revision",job.revision);message.addProperty("width",width);message.addProperty("height",height);message.addProperty("image",image);job.room.publishInventory(job.peer,message);
 }
 public static void renderQueued(Minecraft mc){
  Job job=null;List<Hud> hud;
  synchronized(NativeMenuImages.class){if(!pending.isEmpty()){var it=pending.values().iterator();job=it.next();it.remove();}hud=new ArrayList<>(hudPending.values());hudPending.clear();}
  if(job!=null)try{render(mc,job);}catch(Exception|LinkageError failure){com.mojang.logging.LogUtils.getLogger().debug("Native menu preview unavailable",failure);}
  if(!hud.isEmpty())try{
   if(hudSheet==null)try(var input=mc.getResourceManager().open(new ResourceLocation("minecraft","textures/gui/icons.png"))){byte[] bytes=input.readNBytes(262145);if(bytes.length>262144)return;hudSheet="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes);}
   if(overlays.isEmpty()){
    for(String texture:List.of("fire_1","soul_fire_1")){
     var sprite=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft","block/"+texture));
     overlays.put(texture,ItemIcons.renderImage(mc,16,16,64,64,graphics->graphics.blit(0,0,0,16,16,sprite)));
    }
    try(var input=mc.getResourceManager().open(new ResourceLocation("minecraft","textures/misc/powder_snow_outline.png"))){byte[] bytes=input.readNBytes(262145);if(bytes.length<=262144)overlays.put("frost","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes));}
   }
   for(var target:hud){var message=new JsonObject();message.addProperty("type","nativeHud");message.addProperty("image",hudSheet);overlays.forEach(message::addProperty);target.room.publishInventory(target.peer,message);synchronized(NativeMenuImages.class){if(hudSent.size()>128)hudSent.clear();hudSent.add(target.peer);}}
  }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().debug("Native HUD atlas unavailable",failure);}
 }
 public static synchronized void clear(){pending.clear();hudPending.clear();hudSent.clear();hudSheet=null;overlays.clear();}
}

package dev.craftlink.classroom.client;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import dev.craftlink.skin.*;
public final class UploadedSkins {
 private record Texture(ResourceLocation location,boolean slim){}
 private static final LinkedHashMap<String,Texture> textures=new LinkedHashMap<>(16,.75f,true);
 private static String local;
 private static Texture texture(String id){
  if(!SkinPng.custom(id)||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return null;
  var cached=textures.get(id);if(cached!=null)return cached;
  try{
   var skin=SkinRepository.global().load(id);var image=NativeImage.read(new ByteArrayInputStream(skin.bytes()));
   var location=new ResourceLocation("craftlink","uploaded/"+id.substring(7));Minecraft.getInstance().getTextureManager().register(location,new DynamicTexture(image));
   var texture=new Texture(location,skin.slim());textures.put(id,texture);
   while(textures.size()>24){var first=textures.entrySet().iterator();var entry=first.next();first.remove();Minecraft.getInstance().getTextureManager().release(entry.getValue().location);}
   return texture;
  }catch(Exception e){return null;}
 }
 public static ResourceLocation location(String id){var texture=texture(id);return texture==null?null:texture.location;}
 public static boolean slim(String id){var texture=texture(id);return texture!=null&&texture.slim;}
 public static String localId(){if(local==null)local=SkinRepository.global().local();return local;}
 public static void setLocal(String id)throws java.io.IOException{SkinRepository.global().local(id);local=id;}
}

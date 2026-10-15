package dev.craftlink.classroom.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import dev.craftlink.classroom.ParticipantProfile;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Face previews come from the installed Minecraft resources, without external downloads. */
public final class SkinCatalog {
    private SkinCatalog() {}
    public static JsonObject build(Minecraft mc){
        var result=new JsonObject();result.addProperty("type","skins");var skins=new JsonArray();
        for(String id:ParticipantProfile.SKINS){
            var entry=new JsonObject();entry.addProperty("id",id);
            try(var stream=mc.getResourceManager().open(new ResourceLocation("minecraft","textures/entity/player/wide/"+id+".png"));var texture=NativeImage.read(stream);var face=new NativeImage(8,8,false)){
                for(int y=0;y<8;y++)for(int x=0;x<8;x++){face.setPixelRGBA(x,y,texture.getPixelRGBA(8+x,8+y));face.blendPixel(x,y,texture.getPixelRGBA(40+x,8+y));}
                entry.addProperty("image","data:image/png;base64,"+Base64.getEncoder().encodeToString(face.asByteArray()));
            }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("CraftLink skin preview unavailable: {}",id);}
            skins.add(entry);
        }
        result.add("skins",skins);return result;
    }
}

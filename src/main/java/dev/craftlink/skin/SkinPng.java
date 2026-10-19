package dev.craftlink.skin;
import java.io.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.imageio.ImageIO;
/** Bounded decode; only 64x64 pixels are allocated and metadata is stripped. */
public record SkinPng(String id,byte[] bytes,boolean slim){
 public static SkinPng decode(byte[] bytes,boolean slim)throws IOException{
  if(bytes.length<33||bytes.length>65536)throw new IOException("スキンPNGは64KB以内にしてください。");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("PNGを選択してください。");
   var reader=readers.next();try{
    if(!reader.getFormatName().equalsIgnoreCase("png"))throw new IOException("PNGを選択してください。");
    reader.setInput(input,true,true);if(reader.getWidth(0)!=64||reader.getHeight(0)!=64)throw new IOException("64×64のスキンPNGを選択してください。");
    var image=reader.read(0);var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);byte[] canonical=output.toByteArray();
    var hash=MessageDigest.getInstance("SHA-256");hash.update((byte)(slim?1:0));hash.update(canonical);
    return new SkinPng("custom:"+HexFormat.of().formatHex(hash.digest()),canonical,slim);
   }finally{reader.dispose();}
  }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 public static SkinPng fromData(String data,boolean slim)throws IOException{
  if(data==null||data.length()>90000||!data.startsWith("data:image/png;base64,"))throw new IOException("スキンPNGが不正です。");
  try{return decode(Base64.getDecoder().decode(data.substring(22)),slim);}catch(IllegalArgumentException e){throw new IOException("スキンPNGが不正です。",e);}
 }
 public static boolean custom(String id){return id!=null&&id.matches("custom:[0-9a-f]{64}");}
 public String data(){return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes);}
}

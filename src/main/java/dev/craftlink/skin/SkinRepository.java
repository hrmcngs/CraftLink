package dev.craftlink.skin;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
/** Local content-addressed files: identifiers never become arbitrary filesystem paths. */
public final class SkinRepository {
 private final Path directory;
 public SkinRepository(Path directory){this.directory=directory;}
 private static final class Holder{static final SkinRepository INSTANCE=new SkinRepository(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("craftlink/skins"));}
 public static SkinRepository global(){return Holder.INSTANCE;}
 private Path file(String id,String extension)throws IOException{if(!SkinPng.custom(id))throw new IOException("Unknown skin");return directory.resolve(id.substring(7)+extension);}
 public synchronized void store(SkinPng skin)throws IOException{
  Files.createDirectories(directory);Path file=file(skin.id(),".png");
  if(!Files.exists(file))try(var entries=Files.list(directory)){if(entries.filter(p->p.toString().endsWith(".png")).count()>=64)throw new IOException("保存スキンが64件に達しました。PCのconfig/craftlink/skinsを整理してください。");}
  Files.write(file,skin.bytes());Files.writeString(file(skin.id(),".model"),skin.slim()?"slim":"default");
 }
 public synchronized SkinPng load(String id)throws IOException{
  Path file=file(id,".png");if(!Files.isRegularFile(file)||Files.size(file)>65536)throw new IOException("Skin not found");
  boolean slim=Files.readString(file(id,".model")).equals("slim");var skin=SkinPng.decode(Files.readAllBytes(file),slim);if(!skin.id().equals(id))throw new IOException("Skin checksum mismatch");return skin;
 }
 public synchronized String local(){try{String id=Files.readString(directory.resolve("local.txt")).strip();return SkinPng.custom(id)?id:"";}catch(IOException e){return "";}}
 public synchronized void local(String id)throws IOException{Files.createDirectories(directory);if(!id.isEmpty())load(id);Files.writeString(directory.resolve("local.txt"),id);}
}

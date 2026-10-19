package dev.craftlink.config;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

/** Public identity only. Never stores credentials or grants an authenticated session. */
public final class DevIdentity {
    private DevIdentity(){}
    public static void save(Path file,String name,UUID uuid)throws IOException{
        if(name==null||!name.matches("[A-Za-z0-9_]{1,16}")||uuid==null)throw new IOException("Invalid Minecraft identity");
        Path directory=file.toAbsolutePath().getParent();
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(file))throw new IOException("Identity cache must not be a symbolic link");
        Files.createDirectories(directory);
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid identity directory");
        Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));
        var json=new JsonObject();json.addProperty("name",name);json.addProperty("uuid",uuid.toString());
        Path temporary=Files.createTempFile(directory,"identity-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try{Files.writeString(temporary,json.toString());Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        finally{Files.deleteIfExists(temporary);}
    }
}

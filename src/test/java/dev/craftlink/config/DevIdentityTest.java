package dev.craftlink.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DevIdentityTest {
    @TempDir Path temporary;
    @Test void storesOnlyPublicIdentityAndUsesPrivatePermissions()throws Exception{
        Path file=temporary.resolve("cache/identity.json");UUID id=UUID.randomUUID();
        DevIdentity.save(file,"Player_1",id);
        var data=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(2,data.size());assertEquals("Player_1",data.get("name").getAsString());assertEquals(id.toString(),data.get("uuid").getAsString());
        assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        DevIdentity.save(file,"Player_2",id);assertTrue(Files.readString(file).contains("Player_2"));
    }
    @Test void rejectsMalformedNamesAndLinksWithoutReplacingOtherFiles()throws Exception{
        Path target=temporary.resolve("other.json");Files.writeString(target,"keep");Path link=temporary.resolve("identity.json");Files.createSymbolicLink(link,target);
        assertThrows(java.io.IOException.class,()->DevIdentity.save(link,"Player",UUID.randomUUID()));assertEquals("keep",Files.readString(target));
        assertThrows(java.io.IOException.class,()->DevIdentity.save(target,"bad/name",UUID.randomUUID()));assertEquals("keep",Files.readString(target));
    }
}

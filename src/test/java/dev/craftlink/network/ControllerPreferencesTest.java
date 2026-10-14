package dev.craftlink.network;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControllerPreferencesTest {
    @Test void preferencesUseTheBrowserProtocol() {
        var message = JsonParser.parseString(new ControllerPreferences("classic", .25, 120).message()).getAsJsonObject();
        assertEquals("preferences", message.get("type").getAsString());
        assertEquals("classic", message.get("layout").getAsString());
        assertEquals(.25, message.get("sensitivity").getAsDouble());
        assertEquals(120, message.get("buttonSize").getAsInt());
    }
    @Test void invalidPreferencesAreRejectedBeforeBroadcast() {
        assertThrows(IllegalArgumentException.class, () -> new ControllerPreferences("unknown", .15, 100));
        assertThrows(IllegalArgumentException.class, () -> new ControllerPreferences("mobile", Double.NaN, 100));
        assertThrows(IllegalArgumentException.class, () -> new ControllerPreferences("mobile", .15, 200));
    }
}

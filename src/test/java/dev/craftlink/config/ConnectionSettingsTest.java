package dev.craftlink.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionSettingsTest {
    @Test void usbOverridesLanAddressesAndUsesSameDefaultAsForwarding() {
        var settings = ConnectionSettings.resolve(9999, "0.0.0.0", "my-pc", "usb", "");
        assertEquals(8765, settings.port()); assertEquals("127.0.0.1", settings.bindAddress()); assertEquals("127.0.0.1", settings.advertisedHost());
    }
    @Test void normalLaunchRetainsConfiguredAddressesAndSupportsPortOverride() {
        var settings = ConnectionSettings.resolve(9999, "0.0.0.0", "my-pc", "lan", "");
        assertEquals(9999, settings.port()); assertEquals("0.0.0.0", settings.bindAddress()); assertEquals("my-pc", settings.advertisedHost());
        assertEquals(9876, ConnectionSettings.resolve(9999, "0.0.0.0", "", "usb", "9876").port());
    }
    @Test void invalidRunProfilesCannotProduceConnectionSettings() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionSettings.resolve(8765, "0.0.0.0", "", "other", ""));
        assertThrows(IllegalArgumentException.class, () -> ConnectionSettings.resolve(8765, "0.0.0.0", "", "lan", "65536"));
    }
}

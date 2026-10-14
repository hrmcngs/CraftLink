package dev.craftlink.network;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;

class NetworkAddressesTest {
    @Test void localTransportsIncludeUsbAndPrivateVpnButNotPublicAddresses() throws Exception {
        for (String host : new String[]{"192.168.1.2", "172.20.10.2", "10.0.0.5", "169.254.1.2", "100.64.0.1", "100.127.255.254"}) {
            assertTrue(NetworkAddresses.localAddress(InetAddress.getByName(host)), host);
        }
        for (String host : new String[]{"8.8.8.8", "100.63.255.255", "100.128.0.1", "::1"}) {
            assertFalse(NetworkAddresses.localAddress(InetAddress.getByName(host)), host);
        }
    }
    @Test void advertisedHostCannotInjectAnUrlOrToken() {
        for (String host : new String[]{"127.0.0.1", "100.64.0.2", "my-pc", "pc.private.example"}) assertTrue(NetworkAddresses.validHost(host), host);
        for (String host : new String[]{"", "http://pc", "pc:1234", "pc/#token", "pc@other", "pc/path", "pc\n", "a..b", "-pc", "pc-"}) assertFalse(NetworkAddresses.validHost(host), host);
    }
}

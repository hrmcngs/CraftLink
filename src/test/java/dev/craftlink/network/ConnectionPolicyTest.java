package dev.craftlink.network;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ConnectionPolicyTest {
    @Test void localOnlyAllowsLanUsbAndIpv6LocalPeers(){
        assumeFalse(ConnectionPolicy.remoteEnabled());
        for(String host:new String[]{"127.0.0.1","192.168.1.2","10.2.3.4","172.16.1.2","169.254.1.2","::1","fd12::1","fe80::1"})
            assertTrue(ConnectionPolicy.allows(new InetSocketAddress(host,12345)),host);
        for(String host:new String[]{"8.8.8.8","1.1.1.1","172.32.0.1","2001:4860:4860::8888"})
            assertFalse(ConnectionPolicy.allows(new InetSocketAddress(host,12345)),host);
        assertFalse(ConnectionPolicy.allows(InetSocketAddress.createUnresolved("unknown.invalid",12345)));
        assertFalse(ConnectionPolicy.allows(null));
    }
    @Test void releaseRejectsExternalEndpoints(){
        assumeFalse(ConnectionPolicy.remoteEnabled());
        var controller=new LinkServer();
        var classroom=new dev.craftlink.classroom.ClassroomServer();
        try {
            controller.start(8765,"127.0.0.1","","https://example.invalid");
            classroom.start(8766,"127.0.0.1","","https://example.invalid");
            assertFalse(controller.running());assertFalse(classroom.running());
            assertTrue(controller.status().contains("ローカル"));assertTrue(classroom.status().contains("ローカル"));
        }finally{controller.shutdown();classroom.shutdown();}
    }
}

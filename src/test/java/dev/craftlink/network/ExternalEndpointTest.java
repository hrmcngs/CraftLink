package dev.craftlink.network;

import dev.craftlink.classroom.ClassroomServer;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExternalEndpointTest {
    @Test void normalizesOnlyRootHttpsOrigins() {
        assertEquals("",ExternalEndpoint.normalize(" "));
        assertEquals("https://example.com",ExternalEndpoint.normalize(" HTTPS://EXAMPLE.COM:443/ "));
        assertEquals("https://localhost:9443",ExternalEndpoint.normalize("https://localhost:9443"));
        for(String bad:new String[]{"http://example.com","https://user:pass@example.com","https://example.com/ws","https://example.com/#secret","https://example.com?x=1","https://example.com:0","https://example.com:99999","javascript:alert(1)","https://example.com\r\nInjected: true"})
            assertThrows(IllegalArgumentException.class,()->ExternalEndpoint.normalize(bad),bad);
        assertFalse(ExternalEndpoint.valid(null));
    }
    @Test void externalOriginRequiresExplicitConfigurationAndLoopbackProxy() {
        var local=new InetSocketAddress("127.0.0.1",50000);var lan=new InetSocketAddress("192.168.1.42",50000);
        assertTrue(ExternalEndpoint.allowed(local,"127.0.0.1:8765","https://school.example","https://school.example"));
        assertFalse(ExternalEndpoint.allowed(lan,"school.example","https://school.example","https://school.example"));
        assertFalse(ExternalEndpoint.allowed(local,"school.example","https://other.example","https://school.example"));
        assertFalse(ExternalEndpoint.allowed(local,"school.example","https://school.example",""));
        assertFalse(ExternalEndpoint.allowed(local,"127.0.0.1:8765","http://127.0.0.1:8765","https://school.example"));
        assertTrue(ExternalEndpoint.allowed(lan,"192.168.1.2:8765","http://192.168.1.2:8765",""));
    }
    @Test void normalControllerHttpsQrAndProxiedHandshakeKeepApproval() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(ConnectionPolicy.remoteEnabled());
        var server=new LinkServer();int port=freePort();
        try {
            server.start(port,"0.0.0.0","","https://controller.example/");await(server::running);
            assertTrue(server.url().startsWith("https://controller.example/#"));
            var ws=connect(port,"/ws","https://controller.example");
            ws.sendText("{\"type\":\"hello\",\"token\":\""+server.url().split("#")[1]+"\"}",true).join();await(server::pending);
            assertTrue(server.input.take().held().isEmpty());
            assertThrows(ExecutionException.class,()->connect(port,"/ws","https://wrong.example"));
            ws.sendClose(1000,"done").join();
        } finally {server.shutdown();}
    }
    @Test void classroomHttpsQrAndProxiedHandshakeAutomaticallyJoin() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(ConnectionPolicy.remoteEnabled());
        var server=new ClassroomServer();int port=freePort();
        try {
            server.start(port,"0.0.0.0","","https://classroom.example");await(server::running);
            assertTrue(server.url().startsWith("https://classroom.example/classroom.html#"));
            var ws=connect(port,"/classroom/ws","https://classroom.example");
            ws.sendText("{\"type\":\"hello\",\"token\":\""+server.url().split("#")[1]+"\"}",true).join();await(()->server.room().views().size()==1);
            assertTrue(server.room().views().get(0).approved());assertFalse(server.room().paused());
            assertThrows(ExecutionException.class,()->connect(port,"/classroom/ws","http://127.0.0.1:"+port));
            ws.sendClose(1000,"done").join();
        } finally {server.shutdown();}
    }
    private static int freePort() throws Exception {try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    private static WebSocket connect(int port,String path,String origin) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder().header("Origin",origin)
            .buildAsync(URI.create("ws://127.0.0.1:"+port+path),new WebSocket.Listener(){public void onOpen(WebSocket ws){ws.request(Long.MAX_VALUE);}}).get(3,TimeUnit.SECONDS);
    }
    private static void await(java.util.function.BooleanSupplier check)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!check.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);assertTrue(check.getAsBoolean());}
}

package dev.craftlink.classroom;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassroomServerTest {
    private record Phone(WebSocket socket, LinkedBlockingQueue<String> messages) {}
    @Test void twoWebsocketPhonesControlSeparateInputsAfterApprovalAndPauseStopsBoth() throws Exception {
        var server=new ClassroomServer();
        try {
            server.start(0,"127.0.0.1","");await(server::running);
            URI uri=URI.create(server.url());String origin="http://"+uri.getAuthority();
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(origin+"/classroom.html")).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode());assertTrue(response.body().contains("classroom"));
            Phone a=connect(origin),b=connect(origin);
            a.socket.sendText("{\"type\":\"hello\",\"token\":\""+uri.getFragment()+"\"}",true).join();
            String first=a.messages.poll(3,TimeUnit.SECONDS);assertTrue(first.contains("welcome"));assertTrue(a.messages.poll(3,TimeUnit.SECONDS).contains("\"approved\":true"));
            b.socket.sendText("{\"type\":\"hello\",\"token\":\""+uri.getFragment()+"\"}",true).join();assertTrue(b.messages.poll(3,TimeUnit.SECONDS).contains("welcome"));b.messages.poll(3,TimeUnit.SECONDS);
            var room=server.room();await(()->room.views().size()==2);
            for(var peer:room.views())room.approve(peer.id());room.pause(false);
            a.socket.sendText("{\"type\":\"input\",\"held\":[\"forward\"],\"dx\":0}",true).join();
            b.socket.sendText("{\"type\":\"input\",\"held\":[\"left\"],\"dx\":0}",true).join();
            await(()->{var inputs=room.takeInputs();return inputs.values().stream().anyMatch(p->p.input().held().equals(Set.of("forward")))&&inputs.values().stream().anyMatch(p->p.input().held().equals(Set.of("left")));});
            room.pause(true);assertTrue(room.takeInputs().values().stream().allMatch(p->p.input().held().isEmpty()));
            a.socket.sendText("{\"type\":\"input\",\"held\":[\"osCommand\"],\"dx\":0}",true).join();
            await(()->room.views().stream().filter(ClassroomRoom.View::connected).count()==1);
            assertTrue(room.views().stream().anyMatch(ClassroomRoom.View::connected));
            b.socket.sendClose(1000,"done").join();a.socket.abort();
        }finally{server.shutdown();}
    }
    @Test void foreignOriginIsRejectedBeforeJoining() throws Exception {
        var server=new ClassroomServer();
        try {
            server.start(0,"127.0.0.1","");await(server::running);URI url=URI.create(server.url());
            assertThrows(ExecutionException.class,()->HttpClient.newHttpClient().newWebSocketBuilder().header("Origin","http://elsewhere.invalid")
                .buildAsync(URI.create("ws://"+url.getAuthority()+"/classroom/ws"),new WebSocket.Listener(){}).get(3,TimeUnit.SECONDS));
            assertTrue(server.room().views().isEmpty());
        }finally{server.shutdown();}
    }
    private static Phone connect(String origin) throws Exception {
        var messages=new LinkedBlockingQueue<String>();
        var socket=HttpClient.newHttpClient().newWebSocketBuilder().header("Origin",origin).buildAsync(URI.create(origin.replace("http://","ws://")+"/classroom/ws"),new WebSocket.Listener(){
            public void onOpen(WebSocket ws){ws.request(Long.MAX_VALUE);}
            public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){messages.add(data.toString());return null;}
        }).get(3,TimeUnit.SECONDS);
        return new Phone(socket,messages);
    }
    private static void await(java.util.function.BooleanSupplier check)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!check.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);assertTrue(check.getAsBoolean());}
}

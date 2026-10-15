package dev.craftlink.network;
import dev.craftlink.input.RemoteState;
import dev.craftlink.security.Session;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LinkServerTest {
    @Test void sessionTokensAreIndependent() {
        Session a = new Session(), b = new Session();
        assertTrue(a.accepts(a.token())); assertFalse(a.accepts(b.token())); assertFalse(a.accepts(null));
        assertEquals(43, a.token().length());
    }
    @Test void mailboxBoundsConsumesAndReleases() throws Exception {
        RemoteState state = new RemoteState();
        state.update(Set.of("forward"), 1000, -1000, 3);
        var first = state.take(); assertEquals(180, first.yaw()); assertEquals(-180, first.pitch()); assertEquals(3, first.slot());
        assertEquals(0, state.take().yaw()); assertEquals(-1, state.take().slot());
        Thread.sleep(800); assertTrue(state.take().held().isEmpty());
        state.update(Set.of("attack"), 1, 1, 0); state.clear(); assertTrue(state.take().held().isEmpty());
    }
    @Test void websocketRequiresApprovalAndDisconnectReleasesInput() throws Exception {
        int port; try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        LinkServer server = new LinkServer();
        try {
            server.setAddonActions(java.util.List.of(new dev.craftlink.addon.AddonActions.Action("addon:example:ability", "Ability")));
            server.start(port); await(() -> server.running());
            String origin = "http://127.0.0.1:" + port;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(origin + "/missing")).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            var messages = new LinkedBlockingQueue<String>();
            var frames = new LinkedBlockingQueue<Integer>();
            WebSocket ws = client.newWebSocketBuilder().header("Origin", origin).buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), new WebSocket.Listener() {
                public void onOpen(WebSocket socket) { socket.request(1); }
                public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) { messages.add(data.toString()); socket.request(1); return null; }
                public CompletionStage<?> onBinary(WebSocket socket, java.nio.ByteBuffer data, boolean last) { frames.add(data.remaining()); socket.request(1); return null; }
            }).get(3, TimeUnit.SECONDS);
            assertRejected(client, origin, "invalid");
            assertEquals(0, server.devices());
            String token = server.url().split("#")[1];
            ws.sendText("{\"type\":\"hello\",\"token\":\"" + token + "\"}", true).join();
            assertTrue(messages.poll(3, TimeUnit.SECONDS).contains("pending"));
            String input = "{\"type\":\"input\",\"held\":[\"attack\"],\"dx\":0,\"dy\":0,\"slot\":-1}";
            ws.sendText(input, true).join(); Thread.sleep(80); assertTrue(server.input.take().held().isEmpty());
            assertFalse(server.canStream());
            server.approve(); assertTrue(messages.poll(3, TimeUnit.SECONDS).contains("approved"));
            assertTrue(messages.poll(3, TimeUnit.SECONDS).contains("\"layout\":\"mobile\""));
            assertTrue(messages.poll(3, TimeUnit.SECONDS).contains("addon:example:ability"));
            ws.sendText("{\"type\":\"input\",\"held\":[\"addon:example:ability\"],\"dx\":0,\"dy\":0,\"slot\":-1}", true).join();
            await(() -> server.input.take().held().contains("addon:example:ability"));
            ws.sendText("{\"type\":\"video\",\"enabled\":true}", true).join();
            await(server::canStream);
            long epoch = server.videoEpoch();
            server.publishVideo(epoch, 1, new byte[]{1, 2, 3});
            assertEquals(3, frames.poll(3, TimeUnit.SECONDS));
            assertFalse(server.canStream());
            server.publishVideo(epoch, 2, new byte[]{4});
            assertNull(frames.poll(80, TimeUnit.MILLISECONDS));
            ws.sendText("{\"type\":\"frameAck\",\"frame\":2}", true).join();
            Thread.sleep(80); assertFalse(server.canStream());
            ws.sendText("{\"type\":\"frameAck\",\"frame\":1}", true).join();
            await(server::canStream);
            server.setVideoAllowed(false); assertFalse(server.videoActive());
            server.setControllerPreferences(new ControllerPreferences("classic", .25, 120));
            assertTrue(messages.poll(3, TimeUnit.SECONDS).contains("\"layout\":\"classic\""));
            ws.sendText(input, true).join(); await(() -> server.input.take().held().contains("attack"));
            assertRejected(client, origin, token);
            assertEquals(1, server.devices());
            assertTrue(server.input.take().held().contains("attack"));
            ws.sendClose(1000, "done").join(); await(() -> server.devices() == 0); assertTrue(server.input.take().held().isEmpty());
        } finally { server.shutdown(); }
    }
    @Test void usbLoopbackModeServesControllerAndKeepsTokenWhenCycling() throws Exception {
        LinkServer server = new LinkServer();
        try {
            server.start(0, "127.0.0.1", "127.0.0.1");
            await(server::running);
            String url = server.url();
            assertTrue(url.startsWith("http://127.0.0.1:"));
            server.nextAddress();
            assertEquals(url, server.url());
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url.split("#")[0])).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("CraftLink"));
        } finally { server.shutdown(); }
    }
    @Test void unknownAddonInputClosesConnectionAndReleasesHeldInput() throws Exception {
        var server = new LinkServer();
        try {
            server.start(0, "127.0.0.1", ""); await(server::running);
            String origin = server.url().split("/#")[0], token = server.url().split("#")[1];
            var socket = HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", origin)
                .buildAsync(URI.create(origin.replace("http://", "ws://") + "/ws"), new WebSocket.Listener() {
                    public void onOpen(WebSocket ws) { ws.request(Long.MAX_VALUE); }
                }).get(3, TimeUnit.SECONDS);
            socket.sendText("{\"type\":\"hello\",\"token\":\"" + token + "\"}", true).join();
            await(server::pending); server.approve();
            socket.sendText("{\"type\":\"input\",\"held\":[\"attack\"],\"dx\":0,\"dy\":0,\"slot\":-1}", true).join();
            await(() -> server.input.take().held().contains("attack"));
            socket.sendText("{\"type\":\"input\",\"held\":[\"addon:unknown:ability\"],\"dx\":0,\"dy\":0,\"slot\":-1}", true).join();
            await(() -> server.devices() == 0); assertTrue(server.input.take().held().isEmpty()); socket.abort();
        } finally { server.shutdown(); }
    }
    @Test void customHostIsAdvertisedWithoutChangingBoundInterface() throws Exception {
        LinkServer server = new LinkServer();
        try {
            server.start(0, "127.0.0.1", "usb-pc.example");
            await(server::running);
            assertTrue(server.url().startsWith("http://usb-pc.example:"));
            String token = server.url().split("#")[1];
            server.nextAddress();
            assertEquals(token, server.url().split("#")[1]);
        } finally { server.shutdown(); }
    }
    @Test void eachClientInstanceHasPrivateUrlAndRenewalInvalidatesOldToken() throws Exception {
        LinkServer first = new LinkServer(), second = new LinkServer();
        try {
            first.start(0, "127.0.0.1", ""); second.start(0, "127.0.0.1", "");
            await(first::running); await(second::running);
            String oldUrl = first.url(), oldToken = oldUrl.split("#")[1];
            String secondUrl = second.url();
            assertNotEquals(oldToken, secondUrl.split("#")[1]);
            first.renewSession();
            await(() -> !oldUrl.equals(first.url()));
            assertEquals(oldUrl.split("#")[0], first.url().split("#")[0]);
            assertEquals(secondUrl, second.url());
            assertEquals(0, first.devices());
            assertRejected(HttpClient.newHttpClient(), oldUrl.split("/#")[0], oldToken);
        } finally { first.shutdown(); second.shutdown(); }
    }
    private static void assertRejected(HttpClient client, String origin, String token) throws Exception {
        var closed = new CountDownLatch(1);
        WebSocket other = client.newWebSocketBuilder().header("Origin", origin)
            .buildAsync(URI.create(origin.replace("http://", "ws://") + "/ws"), new WebSocket.Listener() {
                public void onOpen(WebSocket socket) { socket.request(1); }
                public CompletionStage<?> onClose(WebSocket socket, int code, String reason) { closed.countDown(); return null; }
                public void onError(WebSocket socket, Throwable error) { closed.countDown(); }
            }).get(3, TimeUnit.SECONDS);
        other.sendText("{\"type\":\"hello\",\"token\":\"" + token + "\"}", true).join();
        assertTrue(closed.await(3, TimeUnit.SECONDS));
        other.abort();
    }
    private static void await(java.util.function.BooleanSupplier predicate) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!predicate.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(predicate.getAsBoolean());
    }
}

package dev.craftlink.classroom;

import com.google.gson.*;
import dev.craftlink.network.NetworkAddresses;
import dev.craftlink.network.ExternalEndpoint;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

/** Separate endpoint: classroom participants can never control the teacher's player. */
public final class ClassroomServer {
    private final ExecutorService lifecycle = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r,"CraftLink classroom"); t.setDaemon(true); return t; });
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private EventLoopGroup boss, workers;
    private Channel listener;
    private volatile ClassroomRoom room;
    private volatile String status = "停止中", url = "";
    private List<String> hosts = List.of(); private int hostIndex, port;
    private String externalUrl = "";
    public ClassroomRoom room() { return room; }
    public boolean running() { return room != null; }
    public String status() { return status; }
    public String url() { return url; }
    public void start(int requestedPort, String bind, String advertised) { start(requestedPort, bind, advertised, ""); }
    public void start(int requestedPort, String bind, String advertised, String external) {
        String normalized = ExternalEndpoint.normalize(external);
        lifecycle.execute(() -> startInternal(requestedPort, normalized.isEmpty() ? bind : "127.0.0.1", advertised, normalized));
    }
    private synchronized void startInternal(int requestedPort, String bind, String advertised, String external) {
        if (room != null) return;
        status = "起動中";
        ClassroomRoom next = new ClassroomRoom();
        try {
            externalUrl = external;
            hosts = bind.equals("127.0.0.1") && advertised.isEmpty() ? List.of("127.0.0.1") : NetworkAddresses.discover(advertised);
            boss = new NioEventLoopGroup(1); workers = new NioEventLoopGroup(2);
            listener = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true).childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) { channel.pipeline().addLast(new IdleStateHandler(5,0,0),new HttpServerCodec(),new HttpObjectAggregator(8192),new Handler(next, external)); }
                }).bind(bind, requestedPort).sync().channel();
            port = ((InetSocketAddress)listener.localAddress()).getPort(); hostIndex = 0; room = next; updateUrl(); status = "参加受付中";
        } catch (Exception failure) { stopInternal(); status = "起動失敗: " + failure.getClass().getSimpleName(); }
    }
    public synchronized void nextAddress() { if (room != null && !hosts.isEmpty()) { hostIndex = (hostIndex + 1) % hosts.size(); updateUrl(); } }
    private void updateUrl() { url = (externalUrl.isEmpty() ? "http://" + hosts.get(hostIndex) + ":" + port : externalUrl) + "/classroom.html#" + room.token(); }
    public void stop() { lifecycle.execute(this::stopInternal); }
    private synchronized void stopInternal() {
        ClassroomRoom old = room; room = null; if (old != null) old.close();
        if (listener != null) { listener.close(); listener = null; }
        if (boss != null) { boss.shutdownGracefully(0,2,TimeUnit.SECONDS); boss = null; }
        if (workers != null) { workers.shutdownGracefully(0,2,TimeUnit.SECONDS); workers = null; }
        hosts = List.of(); url = externalUrl = ""; status = "停止中";
    }
    public void shutdown() { if (closed.compareAndSet(false,true)) { stop(); lifecycle.shutdown(); } }
    private static final class PeerSocket implements ClassroomRoom.Socket {
        private final Channel channel;
        PeerSocket(Channel channel) { this.channel = channel; }
        public void send(String json) {
            if (!channel.isWritable()) { channel.close(); return; }
            channel.writeAndFlush(new TextWebSocketFrame(json));
        }
        public boolean sendVideo(byte[] packet) {
            if (!channel.isActive() || !channel.isWritable()) return false;
            channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(packet))); return true;
        }
        public void close() { channel.close(); }
    }
    private static final class Handler extends SimpleChannelInboundHandler<Object> {
        private final ClassroomRoom room;
        private final String external;
        private UUID peer;
        private PeerSocket socket;
        private boolean upgraded;
        private long window = System.nanoTime(); private int messages;
        Handler(ClassroomRoom room, String external) { this.room = room; this.external = external; }
        @Override protected void channelRead0(ChannelHandlerContext ctx, Object message) throws Exception {
            if (message instanceof FullHttpRequest request) { http(ctx,request); return; }
            if (message instanceof CloseWebSocketFrame) { ctx.close(); return; }
            if (!upgraded || !(message instanceof TextWebSocketFrame frame) || room.expired()) { ctx.close(); return; }
            if (System.nanoTime() - window >= 1_000_000_000L) { window = System.nanoTime(); messages = 0; }
            if (++messages > 60) { ctx.close(); return; }
            JsonObject data = JsonParser.parseString(frame.text()).getAsJsonObject();
            String type = data.get("type").getAsString();
            if (peer == null) {
                if (!type.equals("hello")) { ctx.close(); return; }
                socket = new PeerSocket(ctx.channel());
                peer = room.join(data.get("token").getAsString(), data.has("resume") ? data.get("resume").getAsString() : "", socket); return;
            }
            switch (type) {
                case "ping" -> socket.send("{\"type\":\"pong\"}");
                case "disconnect" -> { room.remove(peer); ctx.close(); }
                case "video" -> room.video(peer, socket, data.get("enabled").getAsBoolean());
                case "frameAck" -> room.frameAck(peer, socket, data.get("frame").getAsInt());
                case "input" -> {
                    JsonArray buttons = data.getAsJsonArray("held"); if (buttons.size() > ClassroomRoom.BUTTONS.size()) throw new IllegalArgumentException();
                    Set<String> held = new HashSet<>(); for (JsonElement button : buttons) held.add(button.getAsString());
                    room.input(peer, socket, held, data.get("dx").getAsDouble(), data.has("dy") ? data.get("dy").getAsDouble() : 0);
                }
                default -> throw new IllegalArgumentException("Unsupported classroom message");
            }
        }
        private void http(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            if (!request.decoderResult().isSuccess() || request.method() != HttpMethod.GET) { ctx.close(); return; }
            String path = new QueryStringDecoder(request.uri()).path();
            if (path.equals("/classroom/ws")) {
                String host = request.headers().get(HttpHeaderNames.HOST), origin = request.headers().get(HttpHeaderNames.ORIGIN);
                if (!ExternalEndpoint.allowed(ctx.channel().remoteAddress(),host,origin,external) || room.expired()) { ctx.close(); return; }
                var handshaker = new WebSocketServerHandshakerFactory((external.isEmpty() ? "ws://"+host : external.replaceFirst("^https:","wss:"))+path,null,false,2048).newHandshaker(request);
                if (handshaker == null) { ctx.close(); return; }
                handshaker.handshake(ctx.channel(),request); upgraded = true; return;
            }
            if (!path.matches("/[a-zA-Z0-9_./-]*") || path.contains("..")) { ctx.close(); return; }
            String resource = path.equals("/") ? "/web/classroom.html" : "/web"+path;
            byte[] bytes; try (var stream = ClassroomServer.class.getResourceAsStream(resource)) { bytes = stream == null ? null : stream.readAllBytes(); }
            var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,bytes == null ? HttpResponseStatus.NOT_FOUND : HttpResponseStatus.OK,Unpooled.wrappedBuffer(bytes == null ? new byte[0] : bytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE,path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html; charset=utf-8")
                .set(HttpHeaderNames.CONTENT_LENGTH,response.content().readableBytes()).set("Cache-Control","no-store").set("Referrer-Policy","no-referrer").set("X-Content-Type-Options","nosniff")
                .set("Content-Security-Policy","default-src 'self'; connect-src 'self' ws: wss:; style-src 'self'; img-src 'self' blob:; frame-ancestors 'none'");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        @Override public void channelInactive(ChannelHandlerContext ctx) { if (peer != null) room.disconnect(peer,socket); }
        @Override public void userEventTriggered(ChannelHandlerContext ctx,Object event) { ctx.close(); }
        @Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable failure) { ctx.close(); }
    }
}

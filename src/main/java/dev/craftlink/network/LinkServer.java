package dev.craftlink.network;

import com.google.gson.*;
import dev.craftlink.input.RemoteState;
import dev.craftlink.security.Session;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class LinkServer {
    public final RemoteState input = new RemoteState();
    private final ExecutorService lifecycle = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "CraftLink lifecycle"); t.setDaemon(true); return t; });
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private EventLoopGroup boss, workers;
    private Channel listener, device;
    private Session session;
    private volatile boolean running, approved;
    private volatile String status = "停止中", address = "", selectedHost = "";
    private List<String> hosts = List.of();
    private int hostIndex, activePort;
    public String status() { return status; }
    public String url() { return address; }
    public boolean running() { return running; }
    public String selectedHost() { return selectedHost; }
    public synchronized void nextAddress() {
        if (!running || hosts.isEmpty()) return;
        hostIndex = (hostIndex + 1) % hosts.size();
        updateAddress();
    }
    private void updateAddress() {
        selectedHost = hosts.get(hostIndex);
        address = "http://" + selectedHost + ":" + activePort + "/#" + session.token();
    }
    public synchronized boolean pending() { return device != null && !approved; }
    public synchronized int devices() { return device == null ? 0 : 1; }
    public void start(int port) { start(port, "0.0.0.0", ""); }
    public void start(int port, String bindAddress, String advertisedHost) {
        lifecycle.execute(() -> startInternal(port, bindAddress, advertisedHost));
    }
    private synchronized void startInternal(int port, String bindAddress, String advertisedHost) {
        if (running) return;
        status = "起動中";
        try {
            hosts = bindAddress.equals("127.0.0.1") && advertisedHost.isEmpty()
                ? List.of("127.0.0.1") : NetworkAddresses.discover(advertisedHost);
            session = new Session(); boss = new NioEventLoopGroup(1); workers = new NioEventLoopGroup(2);
            listener = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true).childHandler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel ch) { ch.pipeline().addLast(new IdleStateHandler(5, 0, 0), new HttpServerCodec(), new HttpObjectAggregator(8192), new Handler()); }
                }).bind(bindAddress, port).sync().channel();
            activePort = ((InetSocketAddress) listener.localAddress()).getPort();
            hostIndex = 0; updateAddress();
            running = true; status = "接続待ち";
        } catch (Exception e) { stopInternal(); status = "起動失敗: " + e.getClass().getSimpleName(); }
    }
    public void stop() { lifecycle.execute(this::stopInternal); }
    private synchronized void stopInternal() {
        running = false; disconnect();
        if (listener != null) { listener.close(); listener = null; }
        if (boss != null) { boss.shutdownGracefully(0, 2, TimeUnit.SECONDS); boss = null; }
        if (workers != null) { workers.shutdownGracefully(0, 2, TimeUnit.SECONDS); workers = null; }
        session = null; hosts = List.of(); address = selectedHost = ""; status = "停止中";
    }
    public void shutdown() { if (closed.compareAndSet(false, true)) { stop(); lifecycle.shutdown(); } }
    public synchronized void approve() { if (device != null) { approved = true; status = "接続済み"; send(device, "{\"type\":\"approved\"}"); } }
    public synchronized void disconnect() { Channel old = device; device = null; approved = false; input.clear(); if (old != null) { if (old.isActive()) old.writeAndFlush(new TextWebSocketFrame("{\"type\":\"revoked\"}")).addListener(ChannelFutureListener.CLOSE); else old.close(); } if (running) status = "接続待ち"; }
    private static void send(Channel ch, String json) { ch.writeAndFlush(new TextWebSocketFrame(json)); }
    private final class Handler extends SimpleChannelInboundHandler<Object> {
        private boolean upgraded, authenticated;
        private long window = System.nanoTime(); private int messages;
        @Override protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest req) { http(ctx, req); return; }
            if (msg instanceof CloseWebSocketFrame) { ctx.close(); return; }
            if (!(msg instanceof TextWebSocketFrame frame) || !upgraded) { ctx.close(); return; }
            if (System.nanoTime() - window > 1_000_000_000) { window = System.nanoTime(); messages = 0; }
            if (++messages > 100) { ctx.close(); return; }
            JsonObject data = JsonParser.parseString(frame.text()).getAsJsonObject();
            synchronized (LinkServer.this) {
                if (session == null || session.expired()) { ctx.close(); return; }
                if (!authenticated) {
                    if (!"hello".equals(data.get("type").getAsString()) || !session.accepts(data.get("token").getAsString()) || device != null) { ctx.close(); return; }
                    device = ctx.channel(); authenticated = true; status = "Minecraftで接続を許可してください";
                    send(device, "{\"type\":\"pending\"}"); return;
                }
                if (device != ctx.channel()) { ctx.close(); return; }
                String type = data.get("type").getAsString();
                if (type.equals("ping")) { send(device, "{\"type\":\"pong\"}"); return; }
                if (type.equals("disconnect")) { disconnect(); return; }
                if (!approved || !type.equals("input")) return;
                Set<String> keys = new HashSet<>();
                JsonArray buttons = data.getAsJsonArray("held");
                if (buttons.size() > RemoteState.ALLOWED.size()) throw new IllegalArgumentException();
                for (JsonElement b : buttons) { String name = b.getAsString(); if (!RemoteState.ALLOWED.contains(name)) throw new IllegalArgumentException(); keys.add(name); }
                double dx = data.get("dx").getAsDouble(), dy = data.get("dy").getAsDouble();
                int slot = data.get("slot").getAsInt();
                if (!Double.isFinite(dx) || !Double.isFinite(dy) || Math.abs(dx) > 180 || Math.abs(dy) > 180 || slot < -1 || slot > 8) throw new IllegalArgumentException();
                input.update(keys, dx, dy, slot);
            }
        }
        private void http(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            if (!req.decoderResult().isSuccess() || req.method() != HttpMethod.GET) { ctx.close(); return; }
            String path = new QueryStringDecoder(req.uri()).path();
            if (path.equals("/ws")) {
                String host = req.headers().get(HttpHeaderNames.HOST), origin = req.headers().get(HttpHeaderNames.ORIGIN);
                if (host == null || !Objects.equals(origin, "http://" + host)) { ctx.close(); return; }
                WebSocketServerHandshaker handshaker = new WebSocketServerHandshakerFactory("ws://" + host + "/ws", null, false, 4096).newHandshaker(req);
                if (handshaker == null) { ctx.close(); return; }
                handshaker.handshake(ctx.channel(), req); upgraded = true; return;
            }
            if (!path.matches("/[a-zA-Z0-9_./-]*") || path.contains("..")) { ctx.close(); return; }
            String resource = path.equals("/") ? "/web/index.html" : "/web" + path;
            byte[] bytes; try (var stream = LinkServer.class.getResourceAsStream(resource)) { bytes = stream == null ? null : stream.readAllBytes(); }
            var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, bytes == null ? HttpResponseStatus.NOT_FOUND : HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes == null ? new byte[0] : bytes));
            String mime = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html; charset=utf-8";
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, mime).set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes()).set("Cache-Control", "no-store").set("Referrer-Policy", "no-referrer").set("X-Content-Type-Options", "nosniff").set("Content-Security-Policy", "default-src 'self'; connect-src 'self' ws:; style-src 'self'; frame-ancestors 'none'");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        @Override public void channelInactive(ChannelHandlerContext ctx) { synchronized (LinkServer.this) { if (device == ctx.channel()) disconnect(); } }
        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object event) { ctx.close(); }
        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) { ctx.close(); }
    }
}

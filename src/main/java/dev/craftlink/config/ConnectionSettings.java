package dev.craftlink.config;

/** Run profiles override only this launch; persistent Forge config is untouched. */
public record ConnectionSettings(int port, String bindAddress, String advertisedHost) {
    public static ConnectionSettings resolve(int port, String bind, String advertised, String transport, String overridePort) {
        if (!transport.equals("lan") && !transport.equals("usb")) throw new IllegalArgumentException("Unknown CraftLink transport");
        if (transport.equals("usb")) { port = 8765; bind = "127.0.0.1"; advertised = "127.0.0.1"; }
        if (!overridePort.isEmpty()) port = Integer.parseInt(overridePort);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("CraftLink port must be 1024..65535");
        return new ConnectionSettings(port, bind, advertised);
    }
}

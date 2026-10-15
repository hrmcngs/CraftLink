package dev.craftlink.network;

import java.net.*;
import java.util.Locale;

/** Explicit HTTPS origin for a locally terminated tunnel; never trust forwarded headers. */
public final class ExternalEndpoint {
    private ExternalEndpoint() {}
    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Missing external URL");
        value = value.trim();
        if (value.isEmpty()) return "";
        if (value.length() > 512) throw new IllegalArgumentException("External URL too long");
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !NetworkAddresses.validHost(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/")
                || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("Use an HTTPS origin without path or token");
            return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), uri.getPort() == 443 ? -1 : uri.getPort(), null, null, null).toASCIIString();
        } catch (URISyntaxException failure) { throw new IllegalArgumentException("Invalid HTTPS origin", failure); }
    }
    public static boolean valid(Object value) {
        if (!(value instanceof String text)) return false;
        try { normalize(text); return true; } catch (IllegalArgumentException invalid) { return false; }
    }
    public static boolean allowed(SocketAddress remote, String host, String origin, String external) {
        if (host == null || origin == null) return false;
        if (!external.isEmpty()) {
            // A public URL enables tunnel-only mode. cloudflared/proxy must terminate on loopback.
            return remote instanceof InetSocketAddress address && address.getAddress() != null && address.getAddress().isLoopbackAddress()
                && external.equals(origin);
        }
        return origin.equals("http://" + host);
    }
}

package dev.craftlink.security;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class Session {
    private final String token;
    private final long expires = System.nanoTime() + 30L * 60 * 1_000_000_000;
    public Session() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public String token() { return token; }
    public boolean expired() { return System.nanoTime() >= expires; }
    public boolean accepts(String value) { return !expired() && value != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII), value.getBytes(StandardCharsets.US_ASCII)); }
}

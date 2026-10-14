package dev.craftlink.network;

import java.net.*;
import java.util.*;

/** Discover local transports, including Ethernet, USB tethering and private VPNs. */
public final class NetworkAddresses {
    private NetworkAddresses() {}
    public static boolean validHost(String host) {
        return host != null && host.length() <= 253 && host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")
            && Arrays.stream(host.split("\\.", -1)).allMatch(label -> !label.isEmpty() && label.length() <= 63 && !label.startsWith("-") && !label.endsWith("-"));
    }
    public static boolean localAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) return false;
        byte[] bytes = address.getAddress();
        return address.isSiteLocalAddress() || address.isLinkLocalAddress()
            || (Byte.toUnsignedInt(bytes[0]) == 100 && (Byte.toUnsignedInt(bytes[1]) & 192) == 64);
    }
    public static List<String> discover(String preferred) throws SocketException {
        if (!preferred.isEmpty() && !validHost(preferred)) throw new IllegalArgumentException("Invalid advertised host");
        Set<String> hosts = new LinkedHashSet<>();
        if (!preferred.isEmpty()) hosts.add(preferred);
        var interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        // Ordinary interfaces first; still retain virtual VPN adapters as selectable URLs.
        interfaces.sort(Comparator.comparing(NetworkInterface::isVirtual).thenComparing(NetworkInterface::getName));
        for (NetworkInterface nic : interfaces) {
            if (!nic.isUp() || nic.isLoopback()) continue;
            for (InetAddress ip : Collections.list(nic.getInetAddresses())) {
                if (localAddress(ip)) hosts.add(ip.getHostAddress());
            }
        }
        hosts.add("127.0.0.1"); // Android adb reverse, or a locally terminated private tunnel.
        return List.copyOf(hosts);
    }
}

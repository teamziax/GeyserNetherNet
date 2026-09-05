package org.geyser.extension.nethernet.admission;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps the local socket binding separate from the candidate reachable by clients. */
public record ProviderEndpoint(InetSocketAddress bind, InetSocketAddress advertised) {
    public static ProviderEndpoint resolve(InetSocketAddress bind, String advertisedAddress, int advertisedPort) throws IOException {
        boolean selectInterface = (advertisedAddress == null || advertisedAddress.isBlank())
            && !bind.isUnresolved() && bind.getAddress().isAnyLocalAddress();
        return resolve(bind, advertisedAddress, advertisedPort, selectInterface ? interfaces() : List.of());
    }

    static ProviderEndpoint resolve(InetSocketAddress bind, String advertisedAddress, int advertisedPort,
                                    List<InetAddress> interfaces) throws IOException {
        if (bind.isUnresolved() || bind.getPort() < 1 || bind.getAddress().isMulticastAddress())
            throw new IOException("Provider bind-address must resolve to a local unicast or wildcard address and a fixed UDP port");
        if (advertisedPort < 0 || advertisedPort > 65535) throw new IOException("Invalid provider advertised-port");
        int port = advertisedPort == 0 ? bind.getPort() : advertisedPort;
        InetAddress address;
        if (advertisedAddress != null && !advertisedAddress.isBlank()) {
            InetAddress[] resolved = InetAddress.getAllByName(advertisedAddress);
            if (resolved.length != 1) throw new IOException("Set provider.advertised-address to one explicit IP address");
            address = resolved[0];
        } else if (!bind.getAddress().isAnyLocalAddress()) {
            address = bind.getAddress();
        } else {
            List<InetAddress> candidates = interfaces.stream().filter(candidate ->
                !candidate.isAnyLocalAddress() && !candidate.isLoopbackAddress()
                    && !candidate.isLinkLocalAddress() && !candidate.isMulticastAddress()
                    && (candidate instanceof Inet4Address) == (bind.getAddress() instanceof Inet4Address))
                .distinct().toList();
            if (candidates.size() != 1) throw new IOException(
                "Cannot select a unique reachable UDP address; set provider.advertised-address or NETHERNET_PROVIDER_ADVERTISED_ADDRESS");
            address = candidates.getFirst();
        }
        if (address.isAnyLocalAddress() || address.isMulticastAddress() || address.isLinkLocalAddress())
            throw new IOException("Provider advertised-address must be a concrete reachable unicast address");
        if ((address instanceof Inet4Address) != (bind.getAddress() instanceof Inet4Address))
            throw new IOException("Provider bind-address and advertised-address must use the same IP family");
        return new ProviderEndpoint(bind, new InetSocketAddress(address, port));
    }

    private static List<InetAddress> interfaces() throws IOException {
        List<InetAddress> addresses = new ArrayList<>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return addresses;
        for (NetworkInterface network : Collections.list(interfaces))
            if (network.isUp() && !network.isLoopback()) addresses.addAll(Collections.list(network.getInetAddresses()));
        return addresses;
    }
}

package org.geyser.extension.nethernet.admission;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderEndpointTest {
    private static InetAddress address(String value) throws Exception { return InetAddress.getByName(value); }

    @Test void concreteBindingPreservesExistingCandidate() throws Exception {
        var bind = new InetSocketAddress("127.0.0.1", 19133);
        assertEquals(bind, ProviderEndpoint.resolve(bind, "", 0, List.of()).advertised());
    }

    @Test void wildcardCanAdvertiseAnExplicitNatAddressAndPort() throws Exception {
        var endpoint = ProviderEndpoint.resolve(new InetSocketAddress("0.0.0.0", 19133), "203.0.113.9", 29133, List.of());
        assertTrue(endpoint.bind().getAddress().isAnyLocalAddress());
        assertEquals(new InetSocketAddress("203.0.113.9", 29133), endpoint.advertised());
    }

    @Test void onlyAnUnambiguousUsableInterfaceIsAutomaticallyAdvertised() throws Exception {
        var bind = new InetSocketAddress("0.0.0.0", 19133);
        var endpoint = ProviderEndpoint.resolve(bind, "", 0,
            List.of(address("127.0.0.1"), address("169.254.1.1"), address("192.0.2.4")));
        assertEquals("192.0.2.4", endpoint.advertised().getAddress().getHostAddress());
        IOException ambiguous = assertThrows(IOException.class, () -> ProviderEndpoint.resolve(bind, "", 0,
            List.of(address("192.0.2.4"), address("192.0.2.5"))));
        assertTrue(ambiguous.getMessage().contains("NETHERNET_PROVIDER_ADVERTISED_ADDRESS"));
        assertThrows(IOException.class, () -> ProviderEndpoint.resolve(bind, "", 0, List.of()));
    }

    @Test void wildcardCandidateOrWrongAddressFamilyNeverBecomesAProfile() {
        var bind = new InetSocketAddress("0.0.0.0", 19133);
        assertThrows(IOException.class, () -> ProviderEndpoint.resolve(bind, "0.0.0.0", 0, List.of()));
        assertThrows(IOException.class, () -> ProviderEndpoint.resolve(bind, "::1", 0, List.of()));
        assertThrows(IOException.class, () -> ProviderEndpoint.resolve(bind, "169.254.1.1", 0, List.of()));
    }
}

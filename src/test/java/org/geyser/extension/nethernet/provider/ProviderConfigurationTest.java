package org.geyser.extension.nethernet.provider;
import org.geyser.extension.nethernet.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ProviderConfigurationTest {
    @Test void defaultLocalModeAndExplicitProviderConfiguration(@TempDir Path dir) throws Exception {
        var config = ConfigLoader.loadConfig(dir.resolve("config.yml").toFile());
        assertEquals("local", config.mode());
        assertEquals(19133, config.provider().udpPort());
        assertFalse(config.provider().fakeTransport());
        Files.copy(Path.of("examples/provider-fleet.yml"), dir.resolve("fleet.yml"));
        var fleet = ConfigLoader.loadConfig(dir.resolve("fleet.yml").toFile());
        assertEquals("provider", fleet.mode()); assertEquals("EU", fleet.provider().region());
        assertEquals("bootstrap.grant", fleet.provider().bootstrapGrantFile()); assertEquals(100, fleet.provider().capacity());
    }
}

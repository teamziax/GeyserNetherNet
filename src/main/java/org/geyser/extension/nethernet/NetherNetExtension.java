package org.geyser.extension.nethernet;

import dev.kastle.netty.channel.nethernet.NetherNetChannelFactory;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetHTTPSignaling;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import dev.kastle.netty.util.nethernet.NetherNetLogging;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.geyser.extension.nethernet.nethernet.DummyPingChannel;
import org.geyser.extension.nethernet.nethernet.NetherNetChannelInitialiser;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.network.BedrockListener;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.cloudburstmc.netty.warden.*;
import org.geyser.extension.nethernet.provider.*;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;

public class NetherNetExtension implements Extension {
    private static final Channel PING_CHANNEL = new DummyPingChannel();

    private volatile Config config;
    private final Object providerLifecycle = new Object();
    private volatile boolean stopping;
    private volatile ProviderClient providerClient;
    private volatile Supplier<ServerStatus> providerStatusSupplier = this::collectServerStatus;

    private EventLoopGroup eventLoopGroup;
    private Channel netherNetChannel;
    private NetherNetServerSignaling signaling;

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        this.logger().info("Loading %s...".formatted(this.description().name()));

        File configFile = dataFolder().resolve("config.yml").toFile();

        // Ensure the data folder exists
        if (!dataFolder().toFile().exists()) {
            if (!dataFolder().toFile().mkdirs()) {
                this.logger().error("Failed to create data folder, extension will not start!");
                this.disable();
                return;
            }
        }

        // Load our configuration
        try {
            config = ConfigLoader.loadConfig(configFile);
        } catch (IOException e) {
            this.logger().error("Failed to load config, extension will not start!", e);
            this.disable();
            return;
        }

        if (!config.mode().equals("local") && !config.mode().equals("provider")) {
            logger().error("Unknown signalling mode; choose local or provider."); disable(); return;
        }
        if (config.mode().equals("provider")) { startProvider(); return; }

        // Keep libdatachannel's own logging out of the way
        NetherNetLogging.setNativeLogLevel("WARN");

        // Start up NetherNet
        try {
            // Build the base signaling instance
            NetherNetHTTPSignaling.Builder signallingBuilder = new NetherNetHTTPSignaling.Builder()
                .setIdentityKeystore(this.dataFolder().resolve(config.identity().keystore()).toFile(), config.identity().password())
                .setMotdProvider((host, remoteAddress) -> {
                    BedrockPong pong = GeyserImpl.getInstance().getGeyserServer().onQuery(PING_CHANNEL, remoteAddress);

                    return new NetherNetServerSignaling.PongData.Builder()
                        .setServerName(pong.motd())
                        .setProtocol(pong.protocolVersion())
                        .setVersion(pong.version())
                        .setPlayerCount(pong.playerCount())
                        .setMaxPlayerCount(pong.maximumPlayerCount())
                        .build();
                });

            // Enable https if configured to do so
            if (config.https().enabled()) {
                signallingBuilder.setHttpsKeystore(this.dataFolder().resolve(config.https().keystore()).toFile(), config.https().password());
            }

            this.signaling = signallingBuilder.build();

            this.eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

            ServerBootstrap b = new ServerBootstrap();
            b.group(eventLoopGroup)
                .channelFactory(NetherNetChannelFactory.server(signaling))
                .childHandler(new NetherNetChannelInitialiser(GeyserImpl.getInstance()));

            BedrockListener listener = this.geyserApi().bedrockListener();
            this.netherNetChannel = b.bind(new InetSocketAddress(listener.address(), listener.port())).sync().channel();

            this.logger().info("NetherNet listener started on " + (config.https().enabled() ? "https" : "http") + "://" + listener.address() + ":" + listener.port());
        } catch (Exception e) {
            this.logger().error("Failed to start NetherNet", e);
            this.disable();
        }
    }

    private void startProvider() {
        CompletableFuture.runAsync(() -> {
            ProviderStateStore store = null;
            try {
                Config.ProviderConfig settings = config.provider(); URI origin = URI.create(settings.url());
                if (settings.udpPort() < 1 || settings.udpPort() > 65535 || settings.udpPort() == geyserApi().bedrockListener().port()) throw new IOException("Configure a separate NetherNet UDP port");
                var statePath = dataFolder().resolve(settings.stateDirectory());
                ProviderTransport transport;
                if (stopping) return;
                if (settings.fakeTransport()) {
                    if (!java.util.Set.of("127.0.0.1", "localhost", "[::1]").contains(origin.getHost())) throw new IOException("Fake transport requires a loopback provider");
                    transport = new FakeProviderTransport(); logger().warning("Conformance fake transport enabled; this server cannot accept gameplay.");
                } else {
                    ProviderHostFactory factory = ServiceLoader.load(ProviderHostFactory.class, getClass().getClassLoader()).findFirst().orElseThrow(() -> new IOException("Native provider host factory is unavailable; readiness cannot start"));
                    eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
                    ServerBootstrap bootstrap = new ServerBootstrap().group(eventLoopGroup).childHandler(new NetherNetChannelInitialiser(GeyserImpl.getInstance()));
                    ProviderHostFactory.Host host = factory.open(bootstrap, new InetSocketAddress(settings.bindAddress(), settings.udpPort()), Map.of("stateDirectory", statePath.toAbsolutePath().toString(), "profile", settings.profile())).toCompletableFuture().get(30, java.util.concurrent.TimeUnit.SECONDS);
                    netherNetChannel = host.channel(); transport = host.transport();
                    if (stopping) { transport.close(); netherNetChannel.close(); eventLoopGroup.shutdownGracefully(); return; }
                }
                String grant = settings.bootstrapGrantFile().isEmpty() ? null : Files.readString(dataFolder().resolve(settings.bootstrapGrantFile())).trim();
                store = new ProviderStateStore(statePath);
                ProviderClient client = new ProviderClient(new ProviderClient.Configuration(origin, settings.profile(), settings.label(), grant, settings.region().isEmpty() ? null : settings.region(), settings.pool().isEmpty() ? null : settings.pool()), store, transport,
                    () -> providerStatusSupplier.get(), () -> new ProviderClient.Health(true, config.provider().capacity(), Math.min(1, (double) GeyserImpl.getInstance().getSessionManager().size() / Math.max(1, config.provider().capacity())), "nethernet", description().version()), message -> logger().warning(message));
                store = null; // ProviderClient now owns its lifetime.
                synchronized (providerLifecycle) {
                    if (stopping) { client.close(); return; }
                    providerClient = client;
                }
                client.start().whenComplete((registration, failure) -> {
                    if (failure != null) { logger().error("Provider startup failed: " + failure.getClass().getSimpleName()); client.close(); return; }
                    logger().info("Provider instance registered: " + registration.get("instanceId").getAsString());
                    if (registration.has("pendingAction")) logger().info(registration.getAsJsonObject("pendingAction").get("text").getAsString() + " " + registration.getAsJsonObject("pendingAction").get("url").getAsString());
                });
            } catch (Exception e) {
                if (store != null) try { store.close(); } catch (IOException ignored) {}
                logger().error("Provider startup failed: " + e.getMessage());
                if (eventLoopGroup != null) eventLoopGroup.shutdownGracefully();
            }
        });
    }

    private ServerStatus collectServerStatus() {
        GeyserImpl geyser = GeyserImpl.getInstance();
        BedrockPong pong = geyser.getGeyserServer().onQuery(PING_CHANNEL, new InetSocketAddress("127.0.0.1", 0));
        return GeyserStatusCollector.snapshot(pong, geyser.getSessionManager().size(), config.provider().level(), config.provider().gameType());
    }

    /** Programmatic complete status override; panel fixed values still take precedence at the provider. */
    public void setServerStatus(ServerStatus snapshot) { providerStatusSupplier = () -> snapshot; refreshProviderStatus(); }
    public void setServerStatusSupplier(Supplier<ServerStatus> supplier) { providerStatusSupplier = java.util.Objects.requireNonNull(supplier); refreshProviderStatus(); }
    private void refreshProviderStatus() { ProviderClient client = providerClient; if (client != null) client.requestStatusRefresh(); }
    @Subscribe public void onSessionJoin(SessionJoinEvent event) { refreshProviderStatus(); }
    @Subscribe public void onSessionDisconnect(SessionDisconnectEvent event) { refreshProviderStatus(); }
    @Subscribe public void onPostReload(GeyserPostReloadEvent event) {
        try { config = ConfigLoader.loadConfig(dataFolder().resolve("config.yml").toFile()); refreshProviderStatus(); }
        catch (IOException e) { logger().warning("Status configuration refresh failed; retaining previous configuration."); }
    }

    @Subscribe
    public void onGeyserShutdown(GeyserShutdownEvent event) {
        shutdown();
    }

    @Override
    public void disable() {
        shutdown();
        Extension.super.disable();
    }

    private void shutdown() {
        synchronized (providerLifecycle) {
            stopping = true;
            if (this.providerClient != null) { this.providerClient.close(); this.providerClient = null; }
        }
        if (this.netherNetChannel != null) {
            this.netherNetChannel.close();
        }
        if (this.eventLoopGroup != null) {
            this.eventLoopGroup.shutdownGracefully();
        }
    }
}

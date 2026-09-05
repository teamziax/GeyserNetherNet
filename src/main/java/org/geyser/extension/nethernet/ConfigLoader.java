package org.geyser.extension.nethernet;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.interfaces.InterfaceDefaultOptions;
import org.spongepowered.configurate.transformation.ConfigurationTransformation;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;

public class ConfigLoader {
    private static final ConfigurationTransformation.Versioned TRANSFORMER = ConfigurationTransformation.versionedBuilder()
        .versionKey("config-version")
        .addVersion(1, ConfigurationTransformation.builder().build())
        .addVersion(2, ConfigurationTransformation.builder().build())
        .addVersion(4, ConfigurationTransformation.builder()
            .addAction(NodePath.path("provider", "profile"), (path, node) -> {
                if ("warden-admission-v1".equals(node.getString())) node.set("nxs-admission-v1");
                return null;
            }).build())
        .build();

    public static Config loadConfig(File configFile) throws ConfigurateException {
        YamlConfigurationLoader loader = createLoader(configFile);

        CommentedConfigurationNode node = loader.load();
        boolean originallyEmpty = !configFile.exists() || node.isNull();

        int currentVersion = TRANSFORMER.version(node);
        TRANSFORMER.apply(node);
        int newVersion = TRANSFORMER.version(node);

        Config config = node.get(Config.class);

        // Keep ordering
        CommentedConfigurationNode newRoot = CommentedConfigurationNode.root(loader.defaultOptions());
        newRoot.set(config);

        if (originallyEmpty) {
            loader.save(newRoot);
        } else if (currentVersion != newVersion) {
            // Preserve operator fields unknown to this version while filling new defaults.
            node.mergeFrom(newRoot);
            loader.save(node);
        }

        return config;
    }

    private static YamlConfigurationLoader createLoader(File configFile) {
        return YamlConfigurationLoader.builder()
            .file(configFile)
            .indent(2)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions(InterfaceDefaultOptions::addTo)
            .build();
    }
}

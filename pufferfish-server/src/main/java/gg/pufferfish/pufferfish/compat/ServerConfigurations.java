package gg.pufferfish.pufferfish.compat;

import com.google.common.io.Files;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import joptsimple.OptionSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public class ServerConfigurations {

    public static final List<String> hiddenConfigs = List.of(
            "proxies.velocity.secret",
            "web-services.token",
            "sentry-dsn",
            "misc.sentry-dsn",
            "database",
            "server-ip",
            "motd",
            "resource-pack",
            "level-seed",
            "rcon.password",
            "rcon.ip",
            "feature-seeds",
            "world-settings.*.feature-seeds",
            "world-settings.*.seed-*",
            "seed-*"
    );

    private static final List<Pattern> REGEX_PATTERNS = hiddenConfigs.stream()
            .map(s -> Pattern.compile(s.replace(".", "\\.").replace("*", ".*")))
            .toList();

    private static final Map<String, String> CONFIG_FILES = new HashMap<>();
    private static final List<ServerLevel> LEVEL_LIST = new ArrayList<>();

    public static File pufferfishConfig() {
        return (File) requireOptions().valueOf("pufferfish-settings");
    }

    private static OptionSet requireOptions() {
        final OptionSet options = org.bukkit.craftbukkit.Main.options;
        if (options == null) {
            throw new IllegalStateException("Server options not yet parsed");
        }
        return options;
    }

    private static List<String> baseConfigurationFiles() {
        final OptionSet options = requireOptions();
        final File paperConfigDir = (File) options.valueOf("paper-dir");
        return List.of(
                ((File) options.valueOf("config")).getPath(),
                ((File) options.valueOf("bukkit-settings")).getPath(),
                ((File) options.valueOf("spigot-settings")).getPath(),
                paperConfigDir.getPath() + "/paper-global.yml",
                paperConfigDir.getPath() + "/paper-world-defaults.yml",
                pufferfishConfig().getPath()
        );
    }

    public static Map<String, String> getCleanCopies() throws IOException {
        for (final String file : baseConfigurationFiles()) {
            if (CONFIG_FILES.containsKey(file)) {
                continue;
            }
            CONFIG_FILES.put(file, getCleanCopy(file));
        }

        final MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            for (final ServerLevel serverLevel : server.getAllLevels()) {
                if (LEVEL_LIST.contains(serverLevel)) {
                    continue;
                }
                final File worldDir = serverLevel.getWorld().getWorldFolder();
                final String paperWorldConfig = new File(worldDir, "paper-world.yml").getPath();
                final String cleanConfig = getCleanCopy(paperWorldConfig);
                LEVEL_LIST.add(serverLevel);
                if (!cleanConfig.isEmpty()) {
                    CONFIG_FILES.put(paperWorldConfig, cleanConfig);
                }
            }
        }
        return CONFIG_FILES;
    }

    public static boolean matchesRegex(String key) {
        for (final Pattern pattern : REGEX_PATTERNS) {
            if (pattern.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    public static String getCleanCopy(String configName) throws IOException {
        final File file = new File(configName);
        if (!file.exists()) {
            return "";
        }

        switch (Files.getFileExtension(configName)) {
            case "properties": {
                final Properties properties = new Properties();
                try (final FileInputStream inputStream = new FileInputStream(file)) {
                    properties.load(inputStream);
                }
                for (final String hiddenConfig : properties.stringPropertyNames()) {
                    if (matchesRegex(hiddenConfig)) {
                        properties.remove(hiddenConfig);
                    }
                }
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                properties.store(outputStream, "");
                return Arrays.stream(outputStream.toString()
                                .split("\n"))
                        .filter(line -> !line.startsWith("#"))
                        .collect(Collectors.joining("\n"));
            }
            case "yml": {
                final YamlConfiguration configuration = new YamlConfiguration();
                try {
                    configuration.load(file);
                } catch (final InvalidConfigurationException e) {
                    throw new IOException(e);
                }
                configuration.options().header(null);

                for (final String key : configuration.getKeys(true)) {
                    if (matchesRegex(key)) {
                        configuration.set(key, null);
                    }
                }
                if (configuration.getKeys(false).size() == 1) {
                    return "";
                }
                return configuration.saveToString();
            }
            default:
                throw new IllegalArgumentException("Bad file type " + configName);
        }
    }

}

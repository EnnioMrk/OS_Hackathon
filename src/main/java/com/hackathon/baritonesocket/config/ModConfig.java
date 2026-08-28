package com.hackathon.baritonesocket.config;

import com.hackathon.baritonesocket.BaritoneSocketMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ModConfig {
    public int port = 5555;
    public String bindAddress = "127.0.0.1";
    public int maxConnections = 8;
    public int gracePeriodTicks = 100;
    public int settleTicks = 5;
    public int commandTimeoutTicks = 24000;
    public boolean debugLog = false;

    private static final String FILE_NAME = "baritonesocket.properties";

    private ModConfig() {
    }

    public static ModConfig load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        ModConfig config = new ModConfig();
        if (Files.isRegularFile(path)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
                config.port = parseInt(props, "port", config.port);
                config.bindAddress = props.getProperty("bindAddress", config.bindAddress);
                config.maxConnections = parseInt(props, "maxConnections", config.maxConnections);
                config.gracePeriodTicks = parseInt(props, "gracePeriodTicks", config.gracePeriodTicks);
                config.settleTicks = parseInt(props, "settleTicks", config.settleTicks);
                config.commandTimeoutTicks = parseInt(props, "commandTimeoutTicks", config.commandTimeoutTicks);
                config.debugLog = Boolean.parseBoolean(props.getProperty("debugLog", String.valueOf(config.debugLog)));
            } catch (Exception e) {
                BaritoneSocketMod.LOGGER.error("Failed to read config, using defaults", e);
            }
        }
        config.save(path);
        return config;
    }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save(Path path) {
        Properties props = new Properties();
        props.setProperty("port", String.valueOf(port));
        props.setProperty("bindAddress", bindAddress);
        props.setProperty("maxConnections", String.valueOf(maxConnections));
        props.setProperty("gracePeriodTicks", String.valueOf(gracePeriodTicks));
        props.setProperty("settleTicks", String.valueOf(settleTicks));
        props.setProperty("commandTimeoutTicks", String.valueOf(commandTimeoutTicks));
        props.setProperty("debugLog", String.valueOf(debugLog));
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "Baritone Socket Bridge configuration");
        } catch (IOException e) {
            BaritoneSocketMod.LOGGER.error("Failed to write config", e);
        }
    }
}

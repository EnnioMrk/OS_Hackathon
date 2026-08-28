package com.hackathon.baritonesocket;

import com.hackathon.baritonesocket.config.ModConfig;
import com.hackathon.baritonesocket.exec.CommandExecutor;
import com.hackathon.baritonesocket.net.SocketServer;
import com.hackathon.baritonesocket.proto.Protocol;
import com.hackathon.baritonesocket.queue.CommandQueue;
import com.hackathon.baritonesocket.queue.QueuedCommand;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BaritoneSocketMod.MODID)
public final class BaritoneSocketMod {
    public static final String MODID = "baritonesocket";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static BaritoneSocketMod instance;

    private final ModConfig config;
    private final CommandQueue queue;
    private final CommandQueue controlQueue;
    private final CommandExecutor executor;
    private final SocketServer server;
    private final boolean baritoneAvailable;

    public BaritoneSocketMod() {
        instance = this;
        config = ModConfig.load();
        queue = new CommandQueue();
        controlQueue = new CommandQueue();
        executor = new CommandExecutor(queue, controlQueue, config);
        server = new SocketServer(config, queue, controlQueue);
        baritoneAvailable = ModList.get().isLoaded("baritoe");
        LOGGER.info("Baritone Socket Bridge loaded (baritone present: {})", baritoneAvailable);
    }

    public static BaritoneSocketMod get() {
        return instance;
    }

    public void onWorldJoined() {
        queue.clear();
        controlQueue.clear();
        server.start();
    }

    public void onWorldLeft() {
        server.stop();
        executor.clear();
        queue.clear();
        controlQueue.clear();
    }

    public void tick() {
        if (!baritoneAvailable) {
            QueuedCommand cmd;
            while ((cmd = queue.poll()) != null) {
                cmd.source.sendJson(Protocol.error(cmd.id, "baritone is not installed"));
            }
            while ((cmd = controlQueue.poll()) != null) {
                cmd.source.sendJson(Protocol.error(cmd.id, "baritone is not installed"));
            }
            return;
        }
        executor.tick();
    }
}

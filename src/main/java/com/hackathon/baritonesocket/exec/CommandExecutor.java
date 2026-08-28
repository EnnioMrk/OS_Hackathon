package com.hackathon.baritonesocket.exec;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.hackathon.baritonesocket.config.ModConfig;
import com.hackathon.baritonesocket.proto.Protocol;
import com.hackathon.baritonesocket.queue.CommandQueue;
import com.hackathon.baritonesocket.queue.QueuedCommand;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Set;

/**
 * Drains the command queue from the client tick thread (one command at a time,
 * in order) and tracks the active Baritone task until it finishes or fails.
 */
public final class CommandExecutor {
    public static final Set<String> KNOWN_COMMANDS = Set.of(
            "goto", "mine", "follow", "farm", "build", "explore", "stop", "cancel", "set");
    /** Executed immediately, never queued behind a running task. */
    public static final Set<String> CONTROL_COMMANDS = Set.of("stop", "cancel", "set");
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CommandQueue queue;
    private final CommandQueue controlQueue;
    private final ModConfig config;

    private QueuedCommand active;
    private boolean sawActivity;
    private int activityTicks;
    private int idleTicks;

    public CommandExecutor(CommandQueue queue, CommandQueue controlQueue, ModConfig config) {
        this.queue = queue;
        this.controlQueue = controlQueue;
        this.config = config;
    }

    public void tick() {
        QueuedCommand control;
        while ((control = controlQueue.poll()) != null) {
            executeControl(control);
            if (active != null && CONTROL_STOPS_TASK.contains(verb(control.command))) {
                active.source.sendJson(Protocol.done(active.id, "cancelled by '" + control.command + "'"));
                active = null;
                sawActivity = false;
                activityTicks = 0;
                idleTicks = 0;
            }
        }
        if (active == null) {
            QueuedCommand next = queue.poll();
            if (next != null) {
                dispatch(next);
            }
        }
        if (active != null) {
            trackActive();
        }
    }

    private void executeControl(QueuedCommand cmd) {
        LOGGER.info("Dispatching baritone control command '{}'", cmd.command);
        boolean ok;
        try {
            ok = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(cmd.command);
        } catch (Throwable t) {
            LOGGER.error("Baritone threw while executing control command", t);
            cmd.source.sendJson(Protocol.error(cmd.id, "baritone error: " + t));
            return;
        }
        if (!ok) {
            cmd.source.sendJson(Protocol.error(cmd.id, "baritone rejected command '" + cmd.command + "'"));
            return;
        }
        cmd.source.sendJson(Protocol.done(cmd.id, "executed"));
    }

    public void clear() {
        if (active != null) {
            active.source.sendJson(Protocol.error(active.id, "world unloaded, command cancelled"));
        }
        active = null;
        sawActivity = false;
        activityTicks = 0;
        idleTicks = 0;
        QueuedCommand pending;
        while ((pending = controlQueue.poll()) != null) {
            pending.source.sendJson(Protocol.error(pending.id, "world unloaded, command cancelled"));
        }
    }

    private void dispatch(QueuedCommand cmd) {
        LOGGER.info("Dispatching baritone command '{}'", cmd.command);
        boolean ok;
        try {
            ok = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(cmd.command);
        } catch (Throwable t) {
            LOGGER.error("Baritone threw while executing command", t);
            finish(cmd, Protocol.error(cmd.id, "baritone error: " + t));
            return;
        }
        if (!ok) {
            finish(cmd, Protocol.error(cmd.id, "baritone rejected command '" + cmd.command + "'"));
            return;
        }
        if (CONTROL_COMMANDS.contains(verb(cmd.command))) {
            finish(cmd, Protocol.done(cmd.id, "executed"));
            return;
        }
        active = cmd;
        sawActivity = false;
        activityTicks = 0;
        idleTicks = 0;
    }

    private void trackActive() {
        boolean busy;
        try {
            busy = isBaritoneBusy();
        } catch (Throwable t) {
            LOGGER.error("Failed to query baritone state", t);
            finish(active, Protocol.error(active.id, "baritone error: " + t));
            return;
        }
        if (busy) {
            sawActivity = true;
            activityTicks++;
            idleTicks = 0;
            if (activityTicks > config.commandTimeoutTicks) {
                finish(active, Protocol.error(active.id,
                        "command timed out after " + (config.commandTimeoutTicks / 20) + "s of activity"));
            }
        } else {
            idleTicks++;
            if (sawActivity && idleTicks >= config.settleTicks) {
                finish(active, Protocol.done(active.id, "task finished"));
            } else if (!sawActivity && idleTicks >= config.gracePeriodTicks) {
                finish(active, Protocol.done(active.id,
                        "no baritone activity observed; goal may be unreachable or already satisfied"));
            }
        }
    }

    private boolean isBaritoneBusy() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        return baritone.getPathingBehavior().isPathing()
                || baritone.getMineProcess().isActive()
                || baritone.getFollowProcess().isActive()
                || baritone.getFarmProcess().isActive()
                || baritone.getBuilderProcess().isActive()
                || baritone.getExploreProcess().isActive();
    }

    private void finish(QueuedCommand cmd, String json) {
        cmd.source.sendJson(json);
        active = null;
        sawActivity = false;
        activityTicks = 0;
        idleTicks = 0;
    }

    private static final Set<String> CONTROL_STOPS_TASK = Set.of("stop", "cancel");

    private static String verb(String command) {
        int space = command.indexOf(' ');
        String v = space < 0 ? command : command.substring(0, space);
        return v.toLowerCase(Locale.ROOT);
    }
}

package com.hackathon.baritonesocket.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hackathon.baritonesocket.BaritoneSocketMod;
import com.hackathon.baritonesocket.config.ModConfig;
import com.hackathon.baritonesocket.exec.CommandExecutor;
import com.hackathon.baritonesocket.proto.Protocol;
import com.hackathon.baritonesocket.queue.CommandQueue;
import com.hackathon.baritonesocket.queue.QueuedCommand;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class SocketServer {
    private final ModConfig config;
    private final CommandQueue queue;
    private final CommandQueue controlQueue;
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger clientCounter = new AtomicInteger();
    private final AtomicLong autoIdCounter = new AtomicLong();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public SocketServer(ModConfig config, CommandQueue queue, CommandQueue controlQueue) {
        this.config = config;
        this.queue = queue;
        this.controlQueue = controlQueue;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "BaritoneSocket-Acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        for (ClientConnection connection : connections) {
            connection.close();
        }
        connections.clear();
        acceptThread = null;
    }

    public int queueSize() {
        return queue.size();
    }

    public void removeConnection(ClientConnection connection) {
        connections.remove(connection);
    }

    void onLineReceived(ClientConnection connection, String line) {
        if (config.debugLog) {
            BaritoneSocketMod.LOGGER.info("[socket] << {}", line);
        }
        if (line.length() > Protocol.MAX_LINE_LENGTH) {
            connection.sendJson(Protocol.error(null, "line too long (max " + Protocol.MAX_LINE_LENGTH + " chars)"));
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        JsonObject obj;
        try {
            obj = Protocol.parseLine(trimmed);
        } catch (JsonParseException e) {
            connection.sendJson(Protocol.error(null, "malformed JSON: " + e.getMessage()));
            return;
        }
        Long id = Protocol.getId(obj);
        if (id == null) {
            id = autoIdCounter.incrementAndGet();
        }
        String cmd = Protocol.getCmd(obj);
        if (cmd == null || cmd.isBlank()) {
            connection.sendJson(Protocol.error(id, "missing or invalid 'cmd' field"));
            return;
        }
        cmd = cmd.trim();
        String verb = verb(cmd);
        if (!CommandExecutor.KNOWN_COMMANDS.contains(verb)) {
            connection.sendJson(Protocol.error(id, "unknown baritone command '" + verb + "', allowed: "
                    + CommandExecutor.KNOWN_COMMANDS));
            return;
        }
        if (CommandExecutor.CONTROL_COMMANDS.contains(verb)) {
            controlQueue.offer(new QueuedCommand(connection, id, cmd));
            connection.sendJson(Protocol.accepted(id, controlQueue.size()));
            return;
        }
        queue.offer(new QueuedCommand(connection, id, cmd));
        connection.sendJson(Protocol.accepted(id, queue.size()));
    }

    private void acceptLoop() {
        while (running) {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(config.bindAddress, config.port));
                BaritoneSocketMod.LOGGER.info("Baritone socket server listening on {}:{}",
                        config.bindAddress, config.port);
                while (running) {
                    Socket socket = serverSocket.accept();
                    if (connections.size() >= config.maxConnections) {
                        BaritoneSocketMod.LOGGER.warn("Rejecting client, max connections reached");
                        socket.close();
                        continue;
                    }
                    ClientConnection connection = new ClientConnection(socket, this);
                    connections.add(connection);
                    Thread clientThread = new Thread(connection,
                            "BaritoneSocket-Client-" + clientCounter.incrementAndGet());
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (IOException e) {
                if (running) {
                    BaritoneSocketMod.LOGGER.warn("Socket server error: {}, retrying in 2s", e.toString());
                }
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                serverSocket = null;
            }
            if (running) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static String verb(String command) {
        int space = command.indexOf(' ');
        String v = space < 0 ? command : command.substring(0, space);
        return v.toLowerCase(Locale.ROOT);
    }
}

package com.hackathon.baritonesocket.net;

import com.hackathon.baritonesocket.proto.Protocol;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ClientConnection implements Runnable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Socket socket;
    private final SocketServer server;
    private PrintWriter writer;
    private volatile boolean closed;

    ClientConnection(Socket socket, SocketServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            socket.setTcpNoDelay(true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            sendJson(Protocol.hello(server.queueSize()));
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                server.onLineReceived(this, line);
            }
        } catch (IOException e) {
            if (!closed) {
                LOGGER.info("Socket client disconnected: {}", e.toString());
            }
        } finally {
            close();
            server.removeConnection(this);
        }
    }

    /** Thread-safe: called from the socket reader thread and the client tick thread. */
    public synchronized void sendJson(String json) {
        if (closed || writer == null) {
            return;
        }
        writer.println(json);
        if (writer.checkError()) {
            LOGGER.warn("Socket write failed, closing connection");
            close();
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}

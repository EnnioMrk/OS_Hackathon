package com.hackathon.baritonesocket.queue;

import com.hackathon.baritonesocket.net.ClientConnection;

public final class QueuedCommand {
    public final ClientConnection source;
    public final Long id;
    public final String command;

    public QueuedCommand(ClientConnection source, Long id, String command) {
        this.source = source;
        this.id = id;
        this.command = command;
    }
}

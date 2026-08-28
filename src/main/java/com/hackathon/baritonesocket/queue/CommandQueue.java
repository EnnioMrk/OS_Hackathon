package com.hackathon.baritonesocket.queue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread-safe unbounded FIFO. Socket threads produce, the client tick thread consumes. */
public final class CommandQueue {
    private final ConcurrentLinkedQueue<QueuedCommand> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    public void offer(QueuedCommand command) {
        queue.add(command);
        size.incrementAndGet();
    }

    public QueuedCommand poll() {
        QueuedCommand command = queue.poll();
        if (command != null) {
            size.decrementAndGet();
        }
        return command;
    }

    public void clear() {
        queue.clear();
        size.set(0);
    }

    public int size() {
        return size.get();
    }
}

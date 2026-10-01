package dbmonitor.server;

import dbmonitor.common.Alert;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * One connected dashboard (master context v7, sections 8.5, 32-34, 74.2).
 *
 * It owns: one client socket, one ObjectOutputStream, one outgoing queue, one pool thread.
 *
 *   PollerThread -> BroadcastServer.broadcast() -> enqueue() -> [queue] -> run() -> socket
 *
 * The queue separates the server's broadcast speed from this client's network speed: enqueue()
 * returns immediately, so a slow or frozen dashboard can never make the poller wait. And because
 * only this handler's own thread writes to the stream, no locking is needed around the writes.
 *
 * Module owner: M3.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BroadcastServer server;
    private final String name;

    // LinkedBlockingQueue: thread-safe hand-over from the poller thread (producer) to this
    // handler's thread (consumer); take() sleeps until an alert arrives - no busy waiting.
    private final BlockingQueue<Alert> outbox = new LinkedBlockingQueue<Alert>();

    public ClientHandler(Socket socket, BroadcastServer server) {
        this.socket = socket;
        this.server = server;
        this.name = String.valueOf(socket.getRemoteSocketAddress());
    }

    /** Called by the poller thread (through broadcast). Never blocks, never throws. */
    public void enqueue(Alert alert) {
        outbox.offer(alert);
    }

    @Override
    public void run() {
        // One ObjectOutputStream for the whole life of the socket (a second one would corrupt it).
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.flush();   // sends the stream header the dashboard's ObjectInputStream is waiting for
            while (!Thread.currentThread().isInterrupted()) {
                Alert alert = outbox.take();
                out.writeObject(alert);
                out.reset();   // forget sent objects: no memory growth, no stale re-sends
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("[INFO] ClientHandler: dashboard " + name + " disconnected (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // server is stopping; keep the interrupt flag set
        } finally {
            server.removeClient(this);
        }
    }

    /** Closes the socket; used by the server during cleanup and shutdown. */
    void close() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("[WARN] ClientHandler: error closing socket for " + name + ": " + e.getMessage());
        }
    }

    public String getName() {
        return name;
    }
}

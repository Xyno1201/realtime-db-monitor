package dbmonitor.server;

import dbmonitor.common.Alert;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The TCP server that dashboards connect to (master context v7, sections 8.4, 30-31, 74.1).
 *
 * Contract: BroadcastServer(port, maxClients), start(), broadcast(Alert), clientCount(), stop().
 *
 * - start() opens the ServerSocket immediately, so "port already in use" is reported to the
 *   caller at once and a conflicting server never pretends to have started (section 79).
 * - The accept loop runs on its own thread; each accepted dashboard gets a ClientHandler that
 *   runs on the fixed thread pool. Beyond maxClients, new connections are logged and closed.
 * - broadcast() only puts the alert in each handler's queue, so it returns immediately.
 *
 * Module owner: M3.
 */
public class BroadcastServer {

    private final int port;
    private final int maxClients;

    // CopyOnWriteArrayList: the poller iterates this list while the accept thread adds and
    // handler threads remove - iteration works on a snapshot, so no ConcurrentModificationException.
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<ClientHandler>();

    // Fixed pool: one thread per connected dashboard, bounded by maxClients.
    private final ExecutorService pool;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;

    public BroadcastServer(int port, int maxClients) {
        this.port = port;
        this.maxClients = maxClients;
        this.pool = Executors.newFixedThreadPool(maxClients);
    }

    /**
     * Binds the port and starts accepting dashboards on a background thread.
     *
     * @throws IOException if the port cannot be opened (e.g. java.net.BindException: already in use)
     */
    public synchronized void start() throws IOException {
        serverSocket = new ServerSocket(port);   // throws right here if the port is taken
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "AcceptLoop");
        acceptThread.start();
        System.out.println("[INFO] BroadcastServer: listening on port " + port + " (max " + maxClients + " dashboards)");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();   // blocks until a dashboard connects
                if (clients.size() >= maxClients) {
                    System.out.println("[WARN] BroadcastServer: refused " + socket.getRemoteSocketAddress()
                            + " - already " + maxClients + " dashboards connected");
                    socket.close();
                    continue;
                }
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                pool.execute(handler);
                System.out.println("[INFO] BroadcastServer: dashboard connected " + handler.getName()
                        + " (" + clients.size() + " connected)");
            } catch (SocketException e) {
                if (running) {
                    System.err.println("[WARN] BroadcastServer: accept failed: " + e.getMessage());
                }
                // when !running, stop() closed the socket on purpose - the loop simply ends
            } catch (IOException e) {
                System.err.println("[WARN] BroadcastServer: accept failed: " + e.getMessage());
            }
        }
    }

    /** Hands one alert to every connected dashboard's queue. Never waits for a network write. */
    public void broadcast(Alert alert) {
        for (ClientHandler handler : clients) {
            handler.enqueue(alert);
        }
    }

    public int clientCount() {
        return clients.size();
    }

    /** Called by a handler when its dashboard disconnects. Safe to call more than once. */
    void removeClient(ClientHandler handler) {
        if (clients.remove(handler)) {
            handler.close();
            System.out.println("[INFO] BroadcastServer: dashboard removed " + handler.getName()
                    + " (" + clients.size() + " connected)");
        }
    }

    /** Stops accepting, disconnects every dashboard and shuts down the pool (section 44). */
    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();   // unblocks accept()
            } catch (IOException e) {
                System.err.println("[WARN] BroadcastServer: error closing server socket: " + e.getMessage());
            }
        }
        for (ClientHandler handler : clients) {
            handler.close();
        }
        pool.shutdownNow();   // interrupts handlers waiting in take()
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println("[WARN] BroadcastServer: some handlers did not stop within 2 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        clients.clear();
        System.out.println("[INFO] BroadcastServer: stopped");
    }
}

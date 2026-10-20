package dbmonitor.client;

import dbmonitor.common.Alert;

import javax.swing.SwingUtilities;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.net.ConnectException;
import java.net.Socket;

/**
 * The dashboard's network thread: connects, receives Alerts, reconnects (master context v7,
 * sections 35, 36, 75 and 78).
 *
 * <pre>
 *   while running:
 *       new Socket(host, port) -> new ObjectInputStream     (fresh socket + stream every attempt)
 *       Connected
 *       loop: readObject() -> invokeLater(frame.showAlert)   (the EDT updates the cards)
 *       on any failure: Reconnecting, sleep reconnectMs, try again
 * </pre>
 *
 * The protocol is one-way (section 67.7): the server writes, this thread only reads. There is no
 * ObjectOutputStream on the client side.
 *
 * SWING RULE: this thread NEVER touches a Swing component. Every change to the window (alerts and
 * the connection status) is wrapped in SwingUtilities.invokeLater and runs on the EDT.
 *
 * A stale build (ClassNotFoundException / InvalidClassException, e.g. an old out/ folder) is
 * treated like a lost connection: show it in the status line, then reconnect (section 75).
 *
 * Shutdown: shutdown() sets running = false, interrupts the thread (wakes it from sleep) and
 * closes the socket (wakes it from the blocking readObject()).
 *
 * Module owner: Dashboard (Builder B).
 */
public class AlertListenerThread extends Thread {

    private final String host;
    private final int port;
    private final int reconnectMs;
    private final DashboardFrame frame;

    // volatile: written by the EDT in shutdown(), read by this thread in its loops.
    private volatile boolean running = true;

    // volatile: the current socket, so shutdown() (on the EDT) can close it and unblock readObject().
    private volatile Socket socket;

    /** Last reason written to the console; repeated identical failures are logged only once. */
    private String lastLoggedReason;

    public AlertListenerThread(String host, int port, int reconnectMs, DashboardFrame frame) {
        super("AlertListenerThread");
        this.host = host;
        this.port = port;
        this.reconnectMs = reconnectMs;
        this.frame = frame;
    }

    @Override
    public void run() {
        while (running) {
            String reason = connectAndReceive();
            if (!running) {
                break;
            }
            log(reason);
            postReconnecting(reason);
            try {
                Thread.sleep(reconnectMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // shutdown() woke us: keep the flag and leave
                break;
            }
        }
        System.out.println("[INFO] AlertListenerThread: stopped");
    }

    /**
     * One connection attempt: connect, then read alerts until the connection fails.
     *
     * @return a short human-readable reason why the connection ended
     */
    private String connectAndReceive() {
        postConnecting();
        try (Socket s = new Socket(host, port)) {
            socket = s;
            if (!running) {
                return "stopped";   // shutdown() ran while we were connecting
            }
            // The server flushes the stream header right after accepting, so this returns at once.
            try (ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                lastLoggedReason = null;
                System.out.println("[INFO] AlertListenerThread: connected to " + host + ":" + port);
                postConnected();
                while (running) {
                    Object received = in.readObject();
                    // Wire check only: is this object an Alert at all? Which KIND of Alert it is
                    // never matters here - the card asks the Alert itself (polymorphism).
                    if (received instanceof Alert) {
                        postAlert((Alert) received);
                    } else {
                        System.err.println("[WARN] AlertListenerThread: ignored unexpected object of type "
                                + (received == null ? "null" : received.getClass().getName()));
                    }
                }
                return "stopped";
            }
        } catch (ConnectException e) {
            return "server not running";
        } catch (EOFException e) {
            return "server closed the connection";
        } catch (InvalidClassException e) {
            return "class mismatch - rebuild all programs (" + e.getMessage() + ")";
        } catch (IOException e) {
            return "connection lost (" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + ")";
        } catch (ClassNotFoundException e) {
            return "class mismatch - rebuild all programs (missing " + e.getMessage() + ")";
        } finally {
            socket = null;
        }
    }

    /**
     * Stops the thread. Safe to call from any thread (normally the EDT, when the window closes).
     */
    public void shutdown() {
        running = false;
        interrupt();
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                System.err.println("[WARN] AlertListenerThread: error closing socket: " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // Hand-over to the EDT - the only way this thread affects the window
    // =====================================================================

    private void postAlert(final Alert alert) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.showAlert(alert);
            }
        });
    }

    private void postConnecting() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.showConnecting();
            }
        });
    }

    private void postConnected() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.showConnected();
            }
        });
    }

    private void postReconnecting(final String reason) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.showReconnecting(reason, reconnectMs);
            }
        });
    }

    private void log(String reason) {
        if (!reason.equals(lastLoggedReason)) {
            System.out.println("[RECONNECTING] AlertListenerThread: " + reason
                    + " - retrying every " + (reconnectMs / 1000.0) + " s");
            lastLoggedReason = reason;
        }
    }
}

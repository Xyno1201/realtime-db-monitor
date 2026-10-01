package dbmonitor.server;

import dbmonitor.common.Alert;
import dbmonitor.common.AlertStatus;
import dbmonitor.db.AlertDAO;

import java.sql.SQLException;
import java.util.List;

/**
 * The bridge from MySQL to the TCP server (master context v7, sections 8.3, 25-29, 71).
 *
 * Canonical algorithm (section 71), every poll.intervalMs:
 *
 *   rows = dao.findUnpushed()
 *   for each alert:
 *       server.broadcast(alert)              1. BROADCAST FIRST
 *       if alert.status == NEW:
 *           n = dao.markSent(id)             2. then the guarded update
 *           if n == 0: dao.setPushedStatus(id, NEW)   (Admin resolved it meanwhile - do NOT
 *                                                       overwrite; next cycle sends RESOLVED)
 *       else:
 *           dao.setPushedStatus(id, alert.status)
 *
 * Broadcast-then-update gives AT-LEAST-ONCE delivery: if the server dies between the two steps,
 * the alert is sent again after restart (dashboards de-duplicate by id). The other order could
 * lose an alert. Do not swap them (section 26).
 *
 * Scope change SC-1: after each broadcast the push is also recorded in alert_broadcast_log
 * (see AlertDAO.logBroadcast). A failure to write that log is a warning, not a stop.
 *
 * A database failure never ends this thread: it logs, waits one interval and tries again, and the
 * DAO reconnects by itself once MySQL is back (sections 41, 79).
 *
 * Module owner: M2.
 */
public class PollerThread extends Thread {

    private final AlertDAO dao;
    private final BroadcastServer server;
    private final long intervalMs;

    // volatile: written by the shutdown-hook thread, read by this thread
    private volatile boolean running = true;

    private boolean databaseWasDown = false;

    public PollerThread(AlertDAO dao, BroadcastServer server, long intervalMs) {
        super("PollerThread");
        this.dao = dao;
        this.server = server;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        System.out.println("[INFO] PollerThread: started, polling every " + intervalMs + " ms");
        while (running) {
            try {
                pollOnce();
                Thread.sleep(intervalMs);
            } catch (SQLException e) {
                if (!databaseWasDown) {
                    System.err.println("[WARN] PollerThread: database unavailable (" + e.getMessage()
                            + ") - retrying every " + intervalMs + " ms");
                    databaseWasDown = true;
                }
                if (!sleepQuietly()) {
                    break;
                }
            } catch (InterruptedException e) {
                break;   // shutdown() interrupted the sleep
            }
        }
        System.out.println("[INFO] PollerThread: stopped");
    }

    private void pollOnce() throws SQLException {
        List<Alert> rows = dao.findUnpushed();
        if (databaseWasDown) {
            System.out.println("[INFO] PollerThread: database available again - polling resumed");
            databaseWasDown = false;
        }
        for (Alert alert : rows) {
            server.broadcast(alert);
            int dashboards = server.clientCount();
            System.out.println("[INFO] PollerThread: broadcast " + alert.getTypeCode() + " #" + alert.getId()
                    + " (" + alert.getSeverity() + ", " + alert.getStatus() + ") to "
                    + dashboards + " dashboard(s)");

            // Scope change SC-1: record the push in alert_broadcast_log. The broadcast has already
            // happened, so a failed log write must not stop the state update below - warn and go on.
            // (If MySQL is really down, the update below fails too and the outer retry handles it.)
            try {
                dao.logBroadcast(alert, dashboards);
            } catch (SQLException e) {
                System.err.println("[WARN] PollerThread: could not write broadcast log for #" + alert.getId()
                        + ": " + e.getMessage());
            }

            if (alert.getStatus() == AlertStatus.NEW) {
                int changed = dao.markSent(alert.getId());
                if (changed == 0) {
                    // The Admin changed the row after our SELECT (e.g. resolved it). Record what we
                    // actually broadcast; the mismatch makes the next cycle broadcast the new state.
                    dao.setPushedStatus(alert.getId(), AlertStatus.NEW);
                    System.out.println("[INFO] PollerThread: #" + alert.getId()
                            + " changed during broadcast - its new state goes out next cycle");
                }
            } else {
                dao.setPushedStatus(alert.getId(), alert.getStatus());
            }
        }
    }

    /** @return false if interrupted (i.e. we should stop) */
    private boolean sleepQuietly() {
        try {
            Thread.sleep(intervalMs);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /** Stops the loop promptly: clears the flag and wakes the thread if it is sleeping. */
    public void shutdown() {
        running = false;
        interrupt();
    }
}

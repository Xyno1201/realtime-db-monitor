package dbmonitor.server;

import dbmonitor.common.AlertFactory;
import dbmonitor.common.InvalidAlertException;
import dbmonitor.db.ChangeDAO;
import dbmonitor.rules.DataChange;
import dbmonitor.rules.DetectionResult;
import dbmonitor.rules.RuleSet;

import java.sql.SQLException;
import java.util.List;

/**
 * v8 - turns recorded database changes into alerts (HANDOFF.md section 2).
 *
 *   every poll interval:
 *       for each unprocessed change in data_changes (oldest first):
 *           result = rules.evaluate(change)                  (every Rule, polymorphically)
 *           no rule matched  -> mark processed, outcome "No rule matched"
 *           rules matched    -> ONE transaction: insert alert (highest severity) + mark processed
 *
 * The rules are the hard-coded product rules (dbmonitor.rules.ProductRules), passed in by ServerMain.
 *
 * The alert then follows the normal path: PollerThread broadcasts it to the dashboards.
 * This thread never touches sockets, and PollerThread never evaluates rules.
 *
 * It has its OWN ChangeDAO, i.e. its own connection, because it uses transactions: they must
 * never share a connection with the poller's statements.
 *
 * Like the poller, a database failure never ends this thread: warn, wait, retry. Changes made
 * while the server was stopped simply wait in data_changes and are processed on the next start.
 *
 * Module owner: server (Builder A).
 */
public class ChangeDetectorThread extends Thread {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_SOURCE = 50;
    private static final int MAX_MESSAGE = 255;

    private final ChangeDAO dao;
    private final RuleSet rules;          // immutable - safe to share
    private final long intervalMs;

    private volatile boolean running = true;
    private boolean databaseWasDown = false;   // only used by this thread

    public ChangeDetectorThread(ChangeDAO dao, RuleSet rules, long intervalMs) {
        super("ChangeDetectorThread");
        this.dao = dao;
        this.rules = rules;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        System.out.println("[INFO] ChangeDetectorThread: started with " + rules.getRules().size()
                + " product rules, checking for changes every " + intervalMs + " ms");
        while (running) {
            try {
                detectOnce();
                if (databaseWasDown) {
                    System.out.println("[INFO] ChangeDetectorThread: database available again");
                    databaseWasDown = false;
                }
                Thread.sleep(intervalMs);
            } catch (SQLException e) {
                if (!databaseWasDown) {
                    System.err.println("[WARN] ChangeDetectorThread: database unavailable (" + e.getMessage()
                            + ") - retrying every " + intervalMs + " ms");
                    databaseWasDown = true;
                }
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (InterruptedException e) {
                break;   // shutdown() interrupted the sleep
            }
        }
        System.out.println("[INFO] ChangeDetectorThread: stopped");
    }

    /**
     * One pass over the waiting changes. Public so test harnesses can run it without the thread.
     *
     * @return how many alerts were created
     */
    public int detectOnce() throws SQLException {
        List<DataChange> changes = dao.findUnprocessed(BATCH_SIZE);
        int created = 0;
        for (DataChange change : changes) {
            if (process(change)) {
                created++;
            }
        }
        return created;
    }

    private boolean process(DataChange change) throws SQLException {
        if (change.getType() == null) {
            dao.markProcessedWithoutAlert(change.getChangeId(), "IGNORED: unknown change type");
            return false;
        }
        DetectionResult result = rules.evaluate(change);
        if (!result.isAlert()) {
            String why = rules.getRules().isEmpty() ? "No rules active" : "No rule matched";
            dao.markProcessedWithoutAlert(change.getChangeId(), why);
            return false;
        }

        String source = limit(change.getTableName() + " #" + change.getRowId(), MAX_SOURCE);
        String label = change.label(rules.labelFieldFor(change.getTableName()));
        String message = limit(label + ": " + result.reasonsText() + " - by " + change.getChangedBy(), MAX_MESSAGE);
        try {
            String type = AlertFactory.typeForChange(change.getType().name());
            int alertId = dao.createAlertForChange(change.getChangeId(), type, result.getSeverity(), source, message);
            if (alertId > 0) {
                System.out.println("[ALERT] ChangeDetectorThread: alert #" + alertId + " " + result.getSeverity()
                        + " for change #" + change.getChangeId() + " (" + change.getType() + " " + source + "): "
                        + result.reasonsText());
                return true;
            }
            return false;   // already processed elsewhere; nothing written
        } catch (InvalidAlertException e) {
            // should not happen (values are built above) - record it instead of retrying forever
            System.err.println("[WARN] ChangeDetectorThread: change #" + change.getChangeId()
                    + " could not become an alert: " + e.getMessage());
            dao.markProcessedWithoutAlert(change.getChangeId(), "ERROR: " + e.getMessage());
            return false;
        }
    }

    public RuleSet getRules() {
        return rules;
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    private static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}

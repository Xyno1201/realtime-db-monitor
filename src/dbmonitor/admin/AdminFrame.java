package dbmonitor.admin;

import dbmonitor.common.Alert;
import dbmonitor.common.AppConfig;
import dbmonitor.db.AlertDAO;
import dbmonitor.db.BroadcastLogEntry;
import dbmonitor.db.ChangeDAO;
import dbmonitor.rules.DataChange;
import dbmonitor.rules.ProductRules;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The Admin window (v8): the OPERATOR CONSOLE of the monitor.
 *
 *   Alerts         alerts raised by the rules - Resolve / Purge / Delete         (UPDATE, DELETE)
 *   Changes        every change the triggers recorded, and what the rules decided (READ)
 *   Rules          the product rules R1-R10 (read-only, defined in ProductRules.java)
 *   Broadcast Log  every push the server made to the dashboards (audit trail)
 *
 * THREADING: every database call runs in a DbTask (SwingWorker) - never on the Swing thread.
 * One background refresh loads all tabs every admin.refreshMs (javax.swing.Timer fires on the EDT,
 * the worker does the database reads, done() updates the tables on the EDT).
 *
 * The Admin never opens a socket and never talks to the server: it works only with MySQL.
 *
 * Module owner: Admin (Builder A).
 */
public class AdminFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color STATUS_OK = new Color(0x2E7D32);
    private static final Color STATUS_ERROR = new Color(0xC62828);
    private static final int TAB_ALERTS = 0;
    private static final int TAB_CHANGES = 1;
    private static final int TAB_LOG = 3;

    private final transient AlertDAO alertDao;
    private final transient ChangeDAO changeDao;

    private final JTabbedPane tabs = new JTabbedPane();
    private final AlertsPanel alertsPanel;
    private final ChangesPanel changesPanel;
    private final RulesPanel rulesPanel;
    private final BroadcastLogPanel logPanel;
    private final JLabel statusLine = new JLabel(" ");
    private final Timer refreshTimer;

    // EDT-only state
    private boolean refreshInFlight = false;
    private long messageShownAt = 0;

    public AdminFrame(AppConfig config, AlertDAO alertDao, ChangeDAO changeDao) {
        super("Alert Monitor - Operator Console");
        this.alertDao = alertDao;
        this.changeDao = changeDao;

        alertsPanel = new AlertsPanel(this, alertDao);
        changesPanel = new ChangesPanel();
        rulesPanel = new RulesPanel(ProductRules.create());   // fixed rules: shown once
        logPanel = new BroadcastLogPanel();

        tabs.addTab("Alerts", alertsPanel);
        tabs.addTab("Changes", changesPanel);
        tabs.addTab("Rules (" + rulesPanel.count() + ")", rulesPanel);
        tabs.addTab("Broadcast Log", logPanel);

        statusLine.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        statusLine.setFont(statusLine.getFont().deriveFont(Font.BOLD));

        add(tabs, BorderLayout.CENTER);
        add(statusLine, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 680);
        setLocationRelativeTo(null);

        refreshTimer = new Timer(config.getAdminRefreshMs(), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshAll(false);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    /** Called once after the window is shown. */
    public void start() {
        refreshAll(false);
        refreshTimer.start();
    }

    // =====================================================================
    // Refresh - one background task loads every tab
    // =====================================================================

    /** Reloads all tabs. userInitiated = true shows a dialog if the database is unavailable. */
    void refreshAll(boolean userInitiated) {
        if (refreshInFlight) {
            return;   // the previous refresh is still running (e.g. MySQL slow) - don't pile up workers
        }
        refreshInFlight = true;

        new DbTask<RefreshResult>(this, "Refresh", userInitiated) {
            @Override
            protected RefreshResult work() throws SQLException {
                RefreshResult r = new RefreshResult();
                r.alerts = alertDao.readAll();
                r.changes = changeDao.readRecent(ChangesPanel.ROWS_SHOWN);
                r.log = alertDao.readBroadcastLog(BroadcastLogPanel.ROWS_SHOWN);
                return r;
            }

            @Override
            protected void succeeded(RefreshResult r) {
                alertsPanel.setAlerts(r.alerts);
                changesPanel.setChanges(r.changes);
                logPanel.setEntries(r.log);
                tabs.setTitleAt(TAB_ALERTS, "Alerts (" + alertsPanel.count() + ")");
                tabs.setTitleAt(TAB_CHANGES, "Changes (" + changesPanel.count() + ")");
                tabs.setTitleAt(TAB_LOG, "Broadcast Log (" + logPanel.count() + ")");
                if (System.currentTimeMillis() - messageShownAt > 6000) {
                    setStatus("Connected to MySQL - last refresh " + new SimpleDateFormat("HH:mm:ss").format(new Date()),
                            false);
                }
            }

            @Override
            protected void always() {
                refreshInFlight = false;
            }
        }.execute();
    }

    // =====================================================================
    // Status line and failure reporting (used by all tabs)
    // =====================================================================

    /** Shows a message in the status line; it stays visible for 6 s before refresh info returns. */
    void showStatus(String text, boolean error) {
        setStatus(text, error);
        messageShownAt = System.currentTimeMillis();
    }

    private void setStatus(String text, boolean error) {
        statusLine.setText(text);
        statusLine.setForeground(error ? STATUS_ERROR : STATUS_OK);
    }

    /** Called by DbTask on the EDT when a background database task failed. */
    void reportFailure(String action, Throwable cause, boolean dialog) {
        if (cause instanceof SQLException) {
            // MySQL unavailable - the window stays usable and recovers by itself
            System.err.println("[WARN] AdminFrame: " + action + " failed: " + cause.getMessage());
            showStatus("Database unavailable - " + action + " failed (" + cause.getMessage() + ")", true);
            if (dialog) {
                JOptionPane.showMessageDialog(this, action + " failed because the database is unavailable:\n"
                                + cause.getMessage() + "\n\nThe window keeps working - try again once MySQL is running.",
                        "Database unavailable", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // a programming error, not an expected failure: report it loudly
            System.err.println("[ERROR] AdminFrame: unexpected error during " + action + ": " + cause);
            showStatus("Unexpected error during " + action + ": " + cause, true);
            if (dialog) {
                JOptionPane.showMessageDialog(this, "Unexpected error during " + action + ":\n" + cause,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void shutdown() {
        refreshTimer.stop();   // stop timers -> close connections -> dispose
        alertDao.close();
        changeDao.close();
        dispose();
    }

    /** Everything one refresh loads, handed from the worker thread to the EDT. */
    private static final class RefreshResult {
        List<Alert> alerts = new ArrayList<Alert>();
        List<DataChange> changes = new ArrayList<DataChange>();
        List<BroadcastLogEntry> log = new ArrayList<BroadcastLogEntry>();
    }
}

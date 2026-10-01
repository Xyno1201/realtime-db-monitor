package dbmonitor.db;

import dbmonitor.common.Alert;
import dbmonitor.common.AlertFactory;
import dbmonitor.common.AlertStatus;
import dbmonitor.common.AppConfig;
import dbmonitor.common.InvalidAlertException;
import dbmonitor.common.Severity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 3 - the database access boundary (master context v7, sections 8.2, 9, 70, 72).
 *
 * Each AlertDAO owns ONE long-lived JDBC connection (a ManagedConnection) - a connection is not
 * created for every operation. PreparedStatements and ResultSets are short-lived and closed by
 * try-with-resources after every operation.
 *
 * Every public method is synchronized: in the Admin process several SwingWorker threads may call
 * the DAO at the same time, and a JDBC Connection must not be used by two threads at once.
 *
 * Reconnection: ManagedConnection checks the connection with isValid(2) before every operation
 * and reopens it if MySQL was restarted - so the poller and the Admin recover by themselves.
 *
 * All SQL is the canonical SQL of section 70 - do not "improve" it without a contract change.
 * No GUI code and no socket code here (section 8.2).
 *
 * Module owner: M1.
 */
public class AlertDAO implements AutoCloseable {

    private static final String COLUMNS =
            "id, type, severity, source, message, status, pushed_status, created_at";

    // ---- Canonical SQL (section 70) ----
    private static final String SQL_CREATE =
            "INSERT INTO alerts (type, severity, source, message) VALUES (?, ?, ?, ?)";
    private static final String SQL_READ_ALL =
            "SELECT " + COLUMNS + " FROM alerts ORDER BY id DESC";
    // "pushed_status IS NULL" is mandatory: NULL <> 'SENT' is not TRUE in SQL (section 70.3)
    private static final String SQL_FIND_UNPUSHED =
            "SELECT " + COLUMNS + " FROM alerts WHERE pushed_status IS NULL OR status <> pushed_status ORDER BY id ASC";
    // Guard "AND status='NEW'": never overwrite an Admin Resolve (section 29, 70.4)
    private static final String SQL_MARK_SENT =
            "UPDATE alerts SET status = 'SENT', pushed_status = 'SENT' WHERE id = ? AND status = 'NEW'";
    private static final String SQL_SET_PUSHED_STATUS =
            "UPDATE alerts SET pushed_status = ? WHERE id = ?";
    private static final String SQL_RESOLVE =
            "UPDATE alerts SET status = 'RESOLVED' WHERE id = ? AND status <> 'RESOLVED'";
    private static final String SQL_PURGE_RESOLVED =
            "DELETE FROM alerts WHERE status = 'RESOLVED' AND pushed_status = 'RESOLVED'";
    private static final String SQL_DELETE_BY_ID =
            "DELETE FROM alerts WHERE id = ?";

    // ---- Broadcast log (scope change SC-1, HANDOFF.md section 3) ----
    private static final String SQL_LOG_BROADCAST =
            "INSERT INTO alert_broadcast_log (alert_id, type, severity, source, message, broadcast_status, dashboard_count)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_READ_BROADCAST_LOG =
            "SELECT log_id, alert_id, type, severity, source, message, broadcast_status, dashboard_count, broadcast_at"
                    + " FROM alert_broadcast_log ORDER BY log_id DESC LIMIT ?";

    private final ManagedConnection db;   // this DAO's own connection; used only under "this" lock

    /** Ids of bad rows already reported, so each bad row is logged ONCE (section 72.1). */
    private final Set<Integer> reportedBadIds = new HashSet<Integer>();

    /**
     * Does not connect yet. The first operation opens the connection, so a program can start
     * even when MySQL is down (section 79: "DB down at Server startup - Server starts").
     */
    public AlertDAO(AppConfig config) {
        this.db = new ManagedConnection(config, "AlertDAO");
    }

    // =====================================================================
    // CREATE
    // =====================================================================

    /**
     * Inserts a new alert. The database gives it status NEW and pushed_status NULL (schema defaults).
     *
     * @return the generated id
     * @throws InvalidAlertException if the input breaks a validation rule - nothing is inserted
     * @throws SQLException          if MySQL is unavailable or rejects the insert
     */
    public synchronized int create(String type, Severity severity, String source, String message)
            throws InvalidAlertException, SQLException {
        AlertFactory.validateNewAlert(type, severity, source, message);   // validate BEFORE insert

        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, AlertFactory.normalizeType(type));
            ps.setString(2, severity.name());
            ps.setString(3, source.trim());
            ps.setString(4, message.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Insert succeeded but MySQL returned no generated id");
    }

    // =====================================================================
    // READ
    // =====================================================================

    /** All alerts, newest first (Admin table). Never null. Bad rows are skipped and logged once. */
    public synchronized List<Alert> readAll() throws SQLException {
        return query(SQL_READ_ALL);
    }

    /**
     * Alerts whose current status has not been broadcast yet, oldest first (poller).
     * Never null. Bad rows are skipped and logged once.
     */
    public synchronized List<Alert> findUnpushed() throws SQLException {
        return query(SQL_FIND_UNPUSHED);
    }

    // =====================================================================
    // UPDATE
    // =====================================================================

    /**
     * Guarded NEW -> SENT after a broadcast.
     *
     * @return 1 if the row was still NEW and was changed; 0 if someone changed it meanwhile
     *         (e.g. the Admin resolved it) - the caller must NOT assume success (section 29)
     */
    public synchronized int markSent(int id) throws SQLException {
        return update(SQL_MARK_SENT, id);
    }

    /** Records which status was last broadcast. @return rows changed (0 if the row is gone) */
    public synchronized int setPushedStatus(int id, AlertStatus status) throws SQLException {
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(SQL_SET_PUSHED_STATUS)) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            return ps.executeUpdate();
        }
    }

    /**
     * Admin Resolve. Works on NEW or SENT rows (section 12).
     *
     * @return 1 if resolved; 0 if it was already RESOLVED or no longer exists
     */
    public synchronized int resolve(int id) throws SQLException {
        return update(SQL_RESOLVE, id);
    }

    // =====================================================================
    // DELETE
    // =====================================================================

    /** Deletes RESOLVED rows whose RESOLVED state has already been broadcast. @return rows deleted */
    public synchronized int purgeResolved() throws SQLException {
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(SQL_PURGE_RESOLVED)) {
            return ps.executeUpdate();
        }
    }

    /** Admin Delete Selected (any status). @return rows deleted (0 if already gone) */
    public synchronized int deleteById(int id) throws SQLException {
        return update(SQL_DELETE_BY_ID, id);
    }

    // =====================================================================
    // BROADCAST LOG (scope change SC-1) - "where pushed messages go"
    // =====================================================================

    /**
     * Records that {@code alert} was just pushed to {@code dashboardCount} dashboards.
     * Called by the poller right after each broadcast. The alert's values are copied, so the
     * history survives a later Purge of the alert itself.
     *
     * @return the generated log id
     */
    public synchronized int logBroadcast(Alert alert, int dashboardCount) throws SQLException {
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(SQL_LOG_BROADCAST, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, alert.getId());
            ps.setString(2, alert.getTypeCode());
            ps.setString(3, alert.getSeverity().name());
            ps.setString(4, alert.getSource());
            ps.setString(5, alert.getMessage());
            ps.setString(6, alert.getStatus().name());
            ps.setInt(7, dashboardCount);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Broadcast log insert succeeded but MySQL returned no generated id");
    }

    /**
     * The most recent broadcast log rows, newest first (Admin "Broadcast Log" tab). Never null.
     *
     * @param limit maximum number of rows (keeps the table fast as the log grows)
     */
    public synchronized List<BroadcastLogEntry> readBroadcastLog(int limit) throws SQLException {
        Connection conn = connection();
        List<BroadcastLogEntry> entries = new ArrayList<BroadcastLogEntry>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_READ_BROADCAST_LOG)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp at = rs.getTimestamp("broadcast_at");
                    entries.add(new BroadcastLogEntry(
                            rs.getInt("log_id"),
                            rs.getInt("alert_id"),
                            rs.getString("type"),
                            rs.getString("severity"),
                            rs.getString("source"),
                            rs.getString("message"),
                            rs.getString("broadcast_status"),
                            rs.getInt("dashboard_count"),
                            at == null ? 0L : at.getTime()));
                }
            }
        }
        return entries;
    }

    // =====================================================================
    // Connection lifecycle
    // =====================================================================

    /** Closes this DAO's connection. Safe to call more than once. */
    @Override
    public synchronized void close() {
        db.close();
    }

    /** This DAO's live connection (reopened automatically if needed). Caller holds the lock. */
    private Connection connection() throws SQLException {
        return db.get();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private int update(String sql, int id) throws SQLException {
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    private List<Alert> query(String sql) throws SQLException {
        Connection conn = connection();
        List<Alert> alerts = new ArrayList<Alert>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Alert alert = mapRow(rs);
                if (alert != null) {
                    alerts.add(alert);
                }
            }
        }
        return alerts;
    }

    /**
     * Converts the current row into an Alert, or returns null for a bad row (unknown type etc.).
     * A bad row is never broadcast, never updated, never deleted automatically (section 72.1).
     */
    private Alert mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        Timestamp created = rs.getTimestamp("created_at");
        try {
            return AlertFactory.create(
                    id,
                    rs.getString("type"),
                    rs.getString("severity"),
                    rs.getString("source"),
                    rs.getString("message"),
                    rs.getString("status"),
                    rs.getString("pushed_status"),
                    created == null ? 0L : created.getTime());
        } catch (InvalidAlertException e) {
            if (reportedBadIds.add(id)) {   // add() is false when this id was already reported
                System.err.println("[WARN] AlertDAO: skipping alert id=" + id + ": " + e.getMessage());
            }
            return null;
        }
    }
}

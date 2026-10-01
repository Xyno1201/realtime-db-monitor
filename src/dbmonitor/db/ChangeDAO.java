package dbmonitor.db;

import dbmonitor.common.AlertFactory;
import dbmonitor.common.AppConfig;
import dbmonitor.common.InvalidAlertException;
import dbmonitor.common.Severity;
import dbmonitor.rules.ChangeType;
import dbmonitor.rules.DataChange;
import dbmonitor.rules.FieldValue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the changes the TRIGGERS recorded (data_changes + data_change_values) and records what
 * the rules decided about each one.
 *
 * Used by the server's ChangeDetectorThread (its own instance = its own connection) and by the
 * Admin's Changes tab (another instance, another connection).
 *
 * TRANSACTION (the key JDBC point of v8): createAlertForChange() inserts the alert AND marks the
 * change as processed in ONE transaction. Either both happen (commit) or neither (rollback). So a
 * crash in the middle can never produce an alert for a change that still looks unprocessed (which
 * would create a duplicate alert), nor a processed change whose alert is missing.
 *
 * All public methods are synchronized: one DAO = one connection = one user at a time.
 *
 * Module owner: database / rules (Builder A).
 */
public class ChangeDAO implements AutoCloseable {

    private static final String CHANGE_COLUMNS =
            "c.change_id, c.table_name, c.row_id, c.change_type, c.changed_by, c.changed_at, c.processed_at, c.outcome,"
                    + " v.field_name, v.old_value, v.new_value";

    // Oldest unprocessed changes first, with all their column values (LEFT JOIN: a change is read
    // even if, for some reason, it has no value rows).
    private static final String SQL_FIND_UNPROCESSED =
            "SELECT " + CHANGE_COLUMNS
                    + " FROM (SELECT * FROM data_changes WHERE processed_at IS NULL ORDER BY change_id LIMIT ?) c"
                    + " LEFT JOIN data_change_values v ON v.change_id = c.change_id"
                    + " ORDER BY c.change_id, v.field_name";

    private static final String SQL_READ_RECENT =
            "SELECT " + CHANGE_COLUMNS
                    + " FROM (SELECT * FROM data_changes ORDER BY change_id DESC LIMIT ?) c"
                    + " LEFT JOIN data_change_values v ON v.change_id = c.change_id"
                    + " ORDER BY c.change_id DESC, v.field_name";

    private static final String SQL_INSERT_ALERT =
            "INSERT INTO alerts (type, severity, source, message) VALUES (?, ?, ?, ?)";

    // Guard "processed_at IS NULL": a change is processed exactly once.
    private static final String SQL_MARK_PROCESSED =
            "UPDATE data_changes SET processed_at = CURRENT_TIMESTAMP, outcome = ?"
                    + " WHERE change_id = ? AND processed_at IS NULL";

    private final ManagedConnection db;

    public ChangeDAO(AppConfig config) {
        this.db = new ManagedConnection(config, "ChangeDAO");
    }

    /** Changes not yet evaluated, oldest first (server). Never null. */
    public synchronized List<DataChange> findUnprocessed(int limit) throws SQLException {
        return query(SQL_FIND_UNPROCESSED, limit);
    }

    /** The most recent changes, newest first, processed or not (Admin's Changes tab). Never null. */
    public synchronized List<DataChange> readRecent(int limit) throws SQLException {
        return query(SQL_READ_RECENT, limit);
    }

    /**
     * In ONE transaction: insert the alert and mark the change as processed.
     *
     * @return the new alert's id, or 0 if the change had already been processed (nothing is written)
     * @throws InvalidAlertException if the alert values break the validation rules (nothing is written)
     * @throws SQLException          if MySQL fails - the transaction is rolled back, nothing is written
     */
    public synchronized int createAlertForChange(int changeId, String type, Severity severity,
                                                 String source, String message)
            throws InvalidAlertException, SQLException {
        AlertFactory.validateNewAlert(type, severity, source, message);

        Connection conn = db.get();
        conn.setAutoCommit(false);   // start the transaction
        try {
            int alertId;
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ALERT, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, AlertFactory.normalizeType(type));
                ps.setString(2, severity.name());
                ps.setString(3, source.trim());
                ps.setString(4, message.trim());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Alert insert returned no generated id");
                    }
                    alertId = keys.getInt(1);
                }
            }
            int marked;
            try (PreparedStatement ps = conn.prepareStatement(SQL_MARK_PROCESSED)) {
                ps.setString(1, "ALERT #" + alertId + " (" + severity.name() + ")");
                ps.setInt(2, changeId);
                marked = ps.executeUpdate();
            }
            if (marked == 0) {
                conn.rollback();   // someone processed it first - undo the alert insert too
                return 0;
            }
            conn.commit();        // both writes become visible together
            return alertId;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            restoreAutoCommit(conn);
        }
    }

    /**
     * Marks a change as processed without an alert (no rule matched, or it could not be evaluated).
     *
     * @return true if the change was marked; false if it had already been processed
     */
    public synchronized boolean markProcessedWithoutAlert(int changeId, String outcome) throws SQLException {
        Connection conn = db.get();
        try (PreparedStatement ps = conn.prepareStatement(SQL_MARK_PROCESSED)) {
            ps.setString(1, outcome.length() > 255 ? outcome.substring(0, 252) + "..." : outcome);
            ps.setInt(2, changeId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public synchronized void close() {
        db.close();
    }

    // ---------------------------------------------------------------- helpers

    /** Runs a change query and groups the joined rows (one per column) back into DataChange objects. */
    private List<DataChange> query(String sql, int limit) throws SQLException {
        Connection conn = db.get();
        // LinkedHashMap: keeps the SQL order (oldest-first or newest-first) while grouping by id
        Map<Integer, ChangeBuilder> byId = new LinkedHashMap<Integer, ChangeBuilder>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("change_id");
                    ChangeBuilder b = byId.get(id);
                    if (b == null) {
                        b = new ChangeBuilder(rs);
                        byId.put(id, b);
                    }
                    String field = rs.getString("field_name");
                    if (field != null) {
                        b.fields.put(field, new FieldValue(field, rs.getString("old_value"), rs.getString("new_value")));
                    }
                }
            }
        }
        List<DataChange> result = new ArrayList<DataChange>();
        for (ChangeBuilder b : byId.values()) {
            result.add(b.build());
        }
        return result;
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            System.err.println("[WARN] ChangeDAO: rollback failed: " + e.getMessage());
        }
    }

    private static void restoreAutoCommit(Connection conn) {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("[WARN] ChangeDAO: could not restore auto-commit: " + e.getMessage());
        }
    }

    /** Collects one change's columns while the joined rows are read. */
    private static final class ChangeBuilder {
        final int changeId;
        final String table;
        final int rowId;
        final ChangeType type;
        final String changedBy;
        final long changedAt;
        final long processedAt;
        final String outcome;
        final Map<String, FieldValue> fields = new LinkedHashMap<String, FieldValue>();

        ChangeBuilder(ResultSet rs) throws SQLException {
            changeId = rs.getInt("change_id");
            table = rs.getString("table_name");
            rowId = rs.getInt("row_id");
            type = ChangeType.parseOrNull(rs.getString("change_type"));
            changedBy = rs.getString("changed_by");
            Timestamp at = rs.getTimestamp("changed_at");
            changedAt = at == null ? 0L : at.getTime();
            Timestamp done = rs.getTimestamp("processed_at");
            processedAt = done == null ? 0L : done.getTime();
            outcome = rs.getString("outcome");
        }

        DataChange build() {
            return new DataChange(changeId, table, rowId, type, changedBy, changedAt, fields, processedAt, outcome);
        }
    }
}

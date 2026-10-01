import dbmonitor.common.Alert;
import dbmonitor.common.AlertFactory;
import dbmonitor.common.AlertStatus;
import dbmonitor.common.AppConfig;
import dbmonitor.common.RecordAddedAlert;
import dbmonitor.common.InvalidAlertException;
import dbmonitor.common.RecordDeletedAlert;
import dbmonitor.common.Severity;
import dbmonitor.common.RecordChangedAlert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * THROWAWAY HARNESS for Phase 1 + Phase 2 (master context v7, section 84.3).
 * Not part of the application: it lives in test/, not src/.
 *
 * Part A - configuration: loads config/app.properties and prints the validated values.
 * Part B - common model: factory mapping, polymorphism, validation, serialization. No DB needed.
 * Part C - database (Phase 1 steps 8-10): driver present? connection opens? table, columns and
 *          index exist? v8 tables and the 3 products triggers exist? connection closes?
 *          Needs MySQL + sql/setup.sql (run as root) + the
 *          Connector/J jar in lib/. Reported as BLOCKED (not FAIL) if the driver jar is missing.
 *
 * Exit code 0 = all run checks passed, 1 = at least one FAIL.
 */
public class Phase12Check {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Part A: configuration ===");
        AppConfig config = partA();

        System.out.println();
        System.out.println("=== Part B: common alert model ===");
        partB();

        System.out.println();
        System.out.println("=== Part C: database connectivity (Phase 1 steps 8-10) ===");
        partC(config);

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================

    private static AppConfig partA() {
        AppConfig config;
        try {
            config = AppConfig.load(AppConfig.DEFAULT_PATH);
            check(true, "config/app.properties loaded");
        } catch (IOException e) {
            check(false, "config/app.properties could not be read: " + e.getMessage()
                    + " (run from the project root)");
            config = AppConfig.defaults();
        }
        System.out.println("      " + config.describe());
        check(config.getServerPort() == 5050, "server.port = 5050");
        check(config.getServerMaxClients() == 10, "server.maxClients = 10");
        check(config.getPollIntervalMs() == 2000, "poll.intervalMs = 2000");
        check(config.getClientReconnectMs() == 3000, "client.reconnectMs = 3000");
        check(config.getAdminRefreshMs() == 3000, "admin.refreshMs = 3000");
        check(config.getDbUrl().contains("/alert_monitor"), "db.url points at database alert_monitor");
        check(!config.describe().contains(config.getDbPassword()) || config.getDbPassword().isEmpty(),
                "describe() never prints the password");
        return config;
    }

    // =====================================================================

    private static void partB() {
        long now = System.currentTimeMillis();

        // --- 1. Factory maps each DB type string to the right subclass ---
        try {
            Alert added = AlertFactory.create(1, "RECORD_ADDED", "HIGH", "products #21", "Webcam: [R9] new product", "NEW", null, now);
            Alert changed = AlertFactory.create(2, "RECORD_CHANGED", "CRITICAL", "products #3",
                    "Headphones: [R2] price 499.00 -> 49.00 (-90.2%)", "SENT", "SENT", now);
            Alert deleted = AlertFactory.create(3, " record_deleted ", "medium", "products #7",
                    "Desk Lamp: [R5] product deleted", "RESOLVED", "SENT", now);

            check(added instanceof RecordAddedAlert, "RECORD_ADDED -> RecordAddedAlert");
            check(changed instanceof RecordChangedAlert, "RECORD_CHANGED -> RecordChangedAlert");
            check(deleted instanceof RecordDeletedAlert, "' record_deleted ' (spaces, lower case) -> RecordDeletedAlert");

            // --- 2. Polymorphism: same method call, different behaviour per subclass ---
            Alert[] all = {added, changed, deleted};
            Set<String> labels = new HashSet<String>();
            Set<Integer> colours = new HashSet<Integer>();
            for (Alert a : all) {   // only the Alert type is used here - no instanceof
                System.out.println("      " + a.getIconText() + " " + a.getTypeLabel() + " | "
                        + a.getSeverity().getLabel() + " | " + a.getDisplayMessage());
                labels.add(a.getTypeLabel());
                colours.add(a.getColor().getRGB());
            }
            check(labels.size() == 3 && colours.size() == 3, "each subclass has its own label and colour");

            // --- 3. Parsed values ---
            check(added.getPushedStatus() == null && !added.hasBeenPushed(), "pushed_status NULL -> null / never pushed");
            check(deleted.getSeverity() == Severity.MEDIUM, "'medium' -> Severity.MEDIUM");
            check(deleted.isResolved(), "status RESOLVED -> isResolved()");
            check("products #7".equals(deleted.getSource()), "values are stored trimmed");

            // --- 4. Serialization round trip (what the TCP server will do) ---
            Alert copy = roundTrip(changed);
            check(copy instanceof RecordChangedAlert
                            && copy.getId() == 2
                            && copy.getStatus() == AlertStatus.SENT
                            && copy.getPushedStatus() == AlertStatus.SENT
                            && copy.getCreatedAtMillis() == now
                            && copy.getMessage().equals(changed.getMessage()),
                    "serialization round trip keeps subclass and all fields");

        } catch (InvalidAlertException e) {
            check(false, "valid rows were rejected: " + e.getMessage());
        } catch (IOException e) {
            check(false, "serialization failed: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            check(false, "deserialization failed: " + e.getMessage());
        }

        // --- 5. Invalid database rows are rejected with readable messages ---
        String longSource = repeat('s', 51);
        String longMessage = repeat('m', 256);
        expectRejected(0, "RECORD_ADDED", "LOW", "x", "y", "NEW", "id 0");
        expectRejected(9, "BOGUS", "LOW", "x", "y", "NEW", "unknown type BOGUS");
        expectRejected(9, "RECORD_ADDED", "URGENT", "x", "y", "NEW", "unknown severity URGENT");
        expectRejected(9, "RECORD_ADDED", "LOW", "x", "y", "DONE", "unknown status DONE");
        expectRejected(9, "RECORD_ADDED", "LOW", "   ", "y", "NEW", "blank source");
        expectRejected(9, "RECORD_ADDED", "LOW", longSource, "y", "NEW", "51-character source");
        expectRejected(9, "RECORD_ADDED", "LOW", "x", longMessage, "NEW", "256-character message");
        expectRejected(9, null, "LOW", "x", "y", "NEW", "missing type");

        // --- 6. Boundaries that must be ACCEPTED ---
        try {
            AlertFactory.create(9, "RECORD_ADDED", "LOW", repeat('s', 50), repeat('m', 255), "NEW", null, now);
            check(true, "50-character source and 255-character message accepted");
        } catch (InvalidAlertException e) {
            check(false, "boundary values rejected: " + e.getMessage());
        }

        // --- 7. Admin-side validation before INSERT ---
        try {
            AlertFactory.validateNewAlert("RECORD_CHANGED", Severity.CRITICAL, "products #3", "price dropped");
            check(true, "validateNewAlert accepts valid Admin input");
        } catch (InvalidAlertException e) {
            check(false, "validateNewAlert rejected valid input: " + e.getMessage());
        }
        try {
            AlertFactory.validateNewAlert("RECORD_ADDED", Severity.LOW, "products #21", "");
            check(false, "validateNewAlert accepted an empty message");
        } catch (InvalidAlertException e) {
            check(true, "validateNewAlert rejects empty message -> \"" + e.getMessage() + "\"");
        }
        check(AlertFactory.getKnownTypes().length == 3, "exactly 3 known types");
        check(!AlertFactory.isKnownType("BOGUS") && AlertFactory.isKnownType("record_added"), "isKnownType");
    }

    // =====================================================================

    private static void partC(AppConfig config) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("BLOCKED  MySQL Connector/J is not on the classpath.");
            System.out.println("         Put the team-approved Connector/J 8.x jar in lib/ and run again with -cp \"out" + java.io.File.pathSeparator + "lib/*\".");
            System.out.println("         (Parts A and B do not need it.)");
            return;
        }
        check(true, "MySQL JDBC driver found on the classpath");

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
            check(conn.isValid(2), "connection to " + config.getDbUrl() + " is valid");

            DatabaseMetaData meta = conn.getMetaData();
            Set<String> columns = new HashSet<String>();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "alerts", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            String[] required = {"id", "type", "severity", "source", "message", "status", "pushed_status", "created_at"};
            boolean allPresent = true;
            for (String c : required) {
                if (!columns.contains(c)) {
                    allPresent = false;
                    System.out.println("      missing column: " + c);
                }
            }
            check(!columns.isEmpty(), "table 'alerts' exists");
            check(allPresent, "all 8 contract columns present");

            Set<String> indexColumns = new HashSet<String>();
            try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), null, "alerts", false, false)) {
                while (rs.next()) {
                    if ("idx_alerts_status_pushed".equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                        indexColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    }
                }
            }
            check(indexColumns.contains("status") && indexColumns.contains("pushed_status"),
                    "index (status, pushed_status) exists");

            // v8 tables: change capture, broadcast log, and the monitored company table
            String[] tables = {"data_changes", "data_change_values", "alert_broadcast_log", "products"};
            for (String t : tables) {
                boolean exists;
                try (ResultSet rs = meta.getTables(conn.getCatalog(), null, t, null)) {
                    exists = rs.next();
                }
                check(exists, "table '" + t + "' exists"
                        + (exists ? "" : " (run sql/setup.sql as root)"));
            }

            // v8 triggers on products (created as root by sql/setup.sql)
            int triggers = 0;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                            + "WHERE TRIGGER_SCHEMA = DATABASE() AND EVENT_OBJECT_TABLE = 'products'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    triggers = rs.getInt(1);
                }
            }
            check(triggers == 3, "3 change-capture triggers on 'products' (found " + triggers + ")"
                    + (triggers == 3 ? "" : " - run sql/setup.sql as root"));

        } catch (SQLException e) {
            check(false, "database check failed: " + e.getMessage()
                    + " (MySQL running? sql/setup.sql run as root?)");
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    check(true, "connection closed cleanly");
                } catch (SQLException e) {
                    check(false, "connection did not close cleanly: " + e.getMessage());
                }
            }
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void expectRejected(int id, String type, String sev, String source, String message,
                                       String status, String what) {
        try {
            AlertFactory.create(id, type, sev, source, message, status, null, 0L);
            check(false, "should reject " + what);
        } catch (InvalidAlertException e) {
            check(true, "rejects " + what + " -> \"" + e.getMessage() + "\"");
        }
    }

    private static Alert roundTrip(Alert alert) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(alert);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Alert) in.readObject();
        }
    }

    /** Java 8 has no String.repeat. */
    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void check(boolean ok, String description) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + description);
        } else {
            failed++;
            System.out.println("FAIL  " + description);
        }
    }
}

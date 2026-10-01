import dbmonitor.common.Alert;
import dbmonitor.common.AppConfig;
import dbmonitor.common.RecordAddedAlert;
import dbmonitor.common.RecordChangedAlert;
import dbmonitor.common.RecordDeletedAlert;
import dbmonitor.common.Severity;
import dbmonitor.db.AlertDAO;
import dbmonitor.db.ChangeDAO;
import dbmonitor.db.InvalidProductException;
import dbmonitor.db.ProductDAO;
import dbmonitor.rules.ChangeType;
import dbmonitor.rules.DataChange;
import dbmonitor.rules.ProductRules;
import dbmonitor.server.ChangeDetectorThread;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * THROWAWAY HARNESS for the v8 detection pipeline, end to end against the real database:
 *
 *   ProductDAO (the app's CRUD)  ->  MySQL triggers  ->  data_changes / data_change_values
 *   ->  ChangeDetectorThread.detectOnce() (rules)  ->  alerts row + outcome on the change
 *
 * It adds, edits and deletes ONE test product, then removes everything it created.
 *
 * Needs: MySQL running, sql/setup.sql run (as root), the Connector/J
 * jar in lib/, and the SERVER STOPPED (otherwise the server's own detector processes the test
 * changes first and some checks fail).
 *
 * Run from the project root:  java -cp "out;lib/*" DetectionCheck   (':' on Mac/Linux)
 * Exit code 0 = all checks passed, 1 = at least one FAIL.
 */
public class DetectionCheck {

    private static final String NAME = "DetectionCheck Widget";

    private static int passed = 0;
    private static int failed = 0;

    private static final List<Integer> createdAlerts = new ArrayList<Integer>();
    private static int productId = 0;

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.load(AppConfig.DEFAULT_PATH);
        } catch (IOException e) {
            System.out.println("FAIL  cannot read " + AppConfig.DEFAULT_PATH + " (run from the project root)");
            System.exit(1);
            return;
        }
        System.out.println("NOTE  the server must be STOPPED while this runs.");

        ProductDAO products = new ProductDAO(config);
        ChangeDAO changes = new ChangeDAO(config);
        AlertDAO alerts = new AlertDAO(config);
        try {
            ChangeDetectorThread detector = new ChangeDetectorThread(changes, ProductRules.create(), 1000);
            check(detector.getRules().getRules().size() == 10, "detector uses the 10 product rules");
            // Process anything already waiting, so the checks below only see this harness's changes.
            int earlier = detector.detectOnce();
            if (earlier > 0) {
                System.out.println("      (processed " + earlier + " earlier waiting change(s) first)");
            }
            run(products, changes, alerts, detector);
        } catch (SQLException e) {
            check(false, "database error: " + e.getMessage()
                    + " (MySQL running? sql/setup.sql run as root?)");
        } catch (InvalidProductException e) {
            check(false, "valid product input was rejected: " + e.getMessage());
        } finally {
            cleanUp(config, products, alerts);
            products.close();
            changes.close();
            alerts.close();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void run(ProductDAO products, ChangeDAO changes, AlertDAO alerts, ChangeDetectorThread detector)
            throws SQLException, InvalidProductException {

        // ---- 1. CREATE through the app -> trigger records an INSERT ----
        productId = products.create(NAME, "Test", "100.00", "50");
        check(productId > 0, "ProductDAO.create -> product #" + productId);
        DataChange ins = waitingChange(changes, productId, ChangeType.INSERT);
        check(ins != null, "trigger recorded the INSERT in data_changes (waiting, not yet processed)");
        check(ins != null && ins.field("name") != null && NAME.equals(ins.field("name").getNewValue())
                        && ins.field("name").getOldValue() == null,
                "INSERT has new values only (name = '" + NAME + "')");
        check(ins != null && ins.getChangedBy() != null && !ins.getChangedBy().isEmpty(),
                "changed_by is recorded (" + (ins == null ? "-" : ins.getChangedBy()) + ")");

        check(detector.detectOnce() == 1, "detectOnce -> 1 alert");
        Alert a = alertFor(changes, alerts, productId, ChangeType.INSERT);
        check(a instanceof RecordAddedAlert && a.getSeverity() == Severity.LOW && a.getMessage().contains("[R9]"),
                "new product -> RECORD_ADDED, LOW, rule R9" + show(a));

        // ---- 2. UPDATE price 100 -> 40 (-60%) -> R2 CRITICAL ----
        check(products.update(productId, NAME, "Test", "40.00", "50") == 1, "ProductDAO.update price 100 -> 40");
        check(detector.detectOnce() == 1, "detectOnce -> 1 alert");
        a = alertFor(changes, alerts, productId, ChangeType.UPDATE);
        check(a instanceof RecordChangedAlert && a.getSeverity() == Severity.CRITICAL && a.getMessage().contains("[R2]")
                        && ("products #" + productId).equals(a.getSource()) && a.getMessage().startsWith("'" + NAME + "':"),
                "price -60% -> RECORD_CHANGED, CRITICAL, rule R2, source 'products #" + productId + "'" + show(a));

        // ---- 3. an UPDATE that changes nothing is not recorded ----
        int before = countChanges(changes, productId);
        products.update(productId, NAME, "Test", "40.00", "50");
        check(countChanges(changes, productId) == before, "an update that changes no value is NOT recorded by the trigger");

        // ---- 4. an UPDATE that matches no rule: processed, no alert ----
        products.update(productId, NAME, "Test", "45.00", "50");
        check(detector.detectOnce() == 0, "price 40 -> 45 (+12.5%) -> no alert");
        DataChange quiet = latestChange(changes, productId, ChangeType.UPDATE);
        check(quiet != null && quiet.isProcessed() && "No rule matched".equals(quiet.getOutcome()),
                "the change is still marked processed with outcome 'No rule matched'");

        // ---- 5. no double processing ----
        check(detector.detectOnce() == 0, "running detectOnce again creates nothing (each change processed once)");

        // ---- 6. the app refuses bad input (Workbench would not) ----
        before = countChanges(changes, productId);
        try {
            products.update(productId, NAME, "Test", "-5", "50");
            check(false, "a negative price should be rejected by ProductDAO");
        } catch (InvalidProductException e) {
            check(true, "negative price rejected before any SQL: \"" + e.getMessage() + "\"");
        }
        check(countChanges(changes, productId) == before, "nothing was written for the rejected input");

        // ---- 7. DELETE -> R5 HIGH ----
        check(products.delete(productId) == 1, "ProductDAO.delete product #" + productId);
        check(detector.detectOnce() == 1, "detectOnce -> 1 alert");
        a = alertFor(changes, alerts, productId, ChangeType.DELETE);
        check(a instanceof RecordDeletedAlert && a.getSeverity() == Severity.HIGH && a.getMessage().contains("[R5]")
                        && a.getMessage().startsWith("'" + NAME + "':"),
                "delete -> RECORD_DELETED, HIGH, rule R5, labelled with the old name" + show(a));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static DataChange waitingChange(ChangeDAO dao, int rowId, ChangeType type) throws SQLException {
        for (DataChange c : dao.findUnprocessed(500)) {
            if (isOurs(c, rowId, type)) {
                return c;
            }
        }
        return null;
    }

    /** Newest change of that type for our product (readRecent is newest first). */
    private static DataChange latestChange(ChangeDAO dao, int rowId, ChangeType type) throws SQLException {
        for (DataChange c : dao.readRecent(200)) {
            if (isOurs(c, rowId, type)) {
                return c;
            }
        }
        return null;
    }

    private static boolean isOurs(DataChange c, int rowId, ChangeType type) {
        return "products".equalsIgnoreCase(c.getTableName()) && c.getRowId() == rowId && c.getType() == type;
    }

    private static int countChanges(ChangeDAO dao, int rowId) throws SQLException {
        int n = 0;
        for (DataChange c : dao.readRecent(200)) {
            if ("products".equalsIgnoreCase(c.getTableName()) && c.getRowId() == rowId) {
                n++;
            }
        }
        return n;
    }

    /** Follows the change's outcome "ALERT #id (SEV)" to the alert row. */
    private static Alert alertFor(ChangeDAO changes, AlertDAO alerts, int rowId, ChangeType type) throws SQLException {
        DataChange c = latestChange(changes, rowId, type);
        if (c == null || c.getOutcome() == null || !c.getOutcome().startsWith("ALERT #")) {
            System.out.println("      outcome: " + (c == null ? "(change not found)" : c.getOutcome()));
            return null;
        }
        String outcome = c.getOutcome();
        int alertId = Integer.parseInt(outcome.substring("ALERT #".length(), outcome.indexOf(' ', "ALERT #".length())));
        createdAlerts.add(alertId);
        for (Alert a : alerts.readAll()) {
            if (a.getId() == alertId) {
                return a;
            }
        }
        return null;
    }

    private static String show(Alert a) {
        if (a == null) {
            return " (no alert)";
        }
        System.out.println("      alert #" + a.getId() + " " + a.getTypeCode() + " " + a.getSeverity()
                + " | " + a.getSource() + " | " + a.getMessage());
        return "";
    }

    /** Removes the test product, its recorded changes and the alerts it produced. */
    private static void cleanUp(AppConfig config, ProductDAO products, AlertDAO alerts) {
        if (productId <= 0) {
            return;
        }
        try {
            products.delete(productId);   // in case a check failed before step 7
            for (int id : createdAlerts) {
                alerts.deleteById(id);
            }
            try (Connection conn = DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword())) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM data_change_values WHERE change_id IN "
                                + "(SELECT change_id FROM (SELECT change_id FROM data_changes "
                                + "WHERE table_name = 'products' AND row_id = ?) t)")) {
                    ps.setInt(1, productId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM data_changes WHERE table_name = 'products' AND row_id = ?")) {
                    ps.setInt(1, productId);
                    ps.executeUpdate();
                }
            }
            System.out.println("      cleaned up: product #" + productId + ", its changes and "
                    + createdAlerts.size() + " alert(s)");
        } catch (SQLException e) {
            System.out.println("WARN  clean-up failed: " + e.getMessage() + " (run sql/reset-demo.sql to tidy up)");
        }
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

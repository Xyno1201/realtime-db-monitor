import dbmonitor.common.Alert;
import dbmonitor.common.AlertStatus;
import dbmonitor.common.AppConfig;
import dbmonitor.common.RecordChangedAlert;
import dbmonitor.common.InvalidAlertException;
import dbmonitor.common.Severity;
import dbmonitor.db.AlertDAO;
import dbmonitor.db.BroadcastLogEntry;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * THROWAWAY HARNESS for Phase 3 (master context v7, sections 22, 46, 80 M1).
 * Walks the DAO through the whole lifecycle in the order of section 22 against the REAL database,
 * plus the broadcast log (scope change SC-1).
 *
 * RUN IT WITH THE SERVER STOPPED - otherwise the real poller changes the test rows first.
 * It only touches rows it creates. Note: the purge step purges every eligible row, exactly like
 * the Admin's Purge button.
 *
 * Exit code 0 = all passed.
 */
public class DaoCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.load(AppConfig.DEFAULT_PATH);
        } catch (IOException e) {
            System.out.println("FAIL  cannot read config/app.properties: " + e.getMessage());
            System.exit(1);
            return;
        }

        AlertDAO dao = new AlertDAO(config);
        try {
            run(dao);
        } catch (SQLException e) {
            check(false, "database error: " + e.getMessage()
                    + " (MySQL running? schema applied? driver jar in lib/?)");
        } catch (InvalidAlertException e) {
            check(false, "valid input rejected: " + e.getMessage());
        } finally {
            dao.close();
        }
        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void run(AlertDAO dao) throws SQLException, InvalidAlertException {
        // Create
        int id = dao.create("RECORD_CHANGED", Severity.CRITICAL, "  daocheck-01 ", "DaoCheck lifecycle row");
        check(id > 0, "create returns a generated id (" + id + ")");

        // Read + verify NEW / NULL
        Alert a = find(dao.readAll(), id);
        check(a instanceof RecordChangedAlert, "row is read back as a RecordChangedAlert");
        check(a != null && a.getStatus() == AlertStatus.NEW && a.getPushedStatus() == null,
                "new row starts as status NEW, pushed_status NULL");
        check(a != null && "daocheck-01".equals(a.getSource()), "source stored trimmed");

        int id2 = dao.create("RECORD_DELETED", Severity.LOW, "daocheck-02", "second row");
        List<Alert> all = dao.readAll();
        check(!all.isEmpty() && all.get(0).getId() == id2, "readAll returns newest first");

        // Find unpushed
        check(find(dao.findUnpushed(), id) != null, "findUnpushed finds the never-pushed row");

        // Mark SENT (guarded)
        check(dao.markSent(id) == 1, "first markSent changes the NEW row");
        check(dao.markSent(id) == 0, "second guarded markSent changes nothing");
        a = find(dao.readAll(), id);
        check(a != null && a.getStatus() == AlertStatus.SENT && a.getPushedStatus() == AlertStatus.SENT,
                "row is now SENT / SENT");
        check(find(dao.findUnpushed(), id) == null, "a SENT/SENT row is not found as unpushed");

        // Resolve
        check(dao.resolve(id) == 1, "resolve changes a SENT row");
        check(dao.resolve(id) == 0, "resolving again changes nothing");

        // Find changed status
        Alert changed = find(dao.findUnpushed(), id);
        check(changed != null && changed.getStatus() == AlertStatus.RESOLVED,
                "RESOLVED/SENT row is found as unpushed (needs broadcasting)");

        // Purge must wait until RESOLVED was pushed
        dao.purgeResolved();
        check(find(dao.readAll(), id) != null, "purge keeps a RESOLVED row whose RESOLVED state was not pushed");

        // Broadcast log (scope change SC-1): record the push the server would make
        int logId = dao.logBroadcast(changed, 3);
        check(logId > 0, "logBroadcast returns a generated log id (" + logId + ")");
        BroadcastLogEntry entry = findLog(dao.readBroadcastLog(50), logId);
        check(entry != null && entry.getAlertId() == id && "RESOLVED".equals(entry.getBroadcastStatus())
                        && entry.getDashboardCount() == 3 && "RECORD_CHANGED".equals(entry.getType()),
                "readBroadcastLog returns the entry with the copied alert values");

        // Record RESOLVED pushed status
        check(dao.setPushedStatus(id, AlertStatus.RESOLVED) == 1, "setPushedStatus records RESOLVED");

        // Purge
        check(dao.purgeResolved() >= 1 && find(dao.readAll(), id) == null, "purge deletes the RESOLVED/RESOLVED row");
        check(findLog(dao.readBroadcastLog(50), logId) != null, "broadcast log entry survives the purge (history kept)");

        // Resolve before first poll + delete selected
        check(dao.resolve(id2) == 1, "a NEW row can be resolved before its first poll");
        check(dao.deleteById(id2) == 1, "deleteById removes a row");
        check(dao.deleteById(id2) == 0, "deleteById on a missing row returns 0");

        // Validation before insert
        try {
            dao.create("RECORD_ADDED", Severity.LOW, "daocheck", "   ");
            check(false, "blank message should be rejected");
        } catch (InvalidAlertException e) {
            check(true, "blank message rejected before insert: \"" + e.getMessage() + "\"");
        }
        try {
            dao.create("BOGUS", Severity.LOW, "daocheck", "x");
            check(false, "unknown type should be rejected");
        } catch (InvalidAlertException e) {
            check(true, "unknown type rejected before insert");
        }
    }

    private static BroadcastLogEntry findLog(List<BroadcastLogEntry> entries, int logId) {
        for (BroadcastLogEntry e : entries) {
            if (e.getLogId() == logId) {
                return e;
            }
        }
        return null;
    }

    private static Alert find(List<Alert> alerts, int id) {
        for (Alert a : alerts) {
            if (a.getId() == id) {
                return a;
            }
        }
        return null;
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

package dbmonitor.db;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * One row of the alert_broadcast_log table (scope change SC-1, HANDOFF.md section 3):
 * a record that the server pushed a particular alert, in a particular status, at a particular
 * time, to a number of dashboards.
 *
 * Deliberately a plain read-only value object, NOT an Alert:
 *   - it describes a past event, not a live alert, so it is never broadcast or serialized;
 *   - its values are copies taken at broadcast time, stored as plain text, so an old log row
 *     can always be displayed even if the alert itself has since been purged.
 *
 * Immutable (final fields, no setters) - safe to hand from a SwingWorker thread to the EDT.
 *
 * Module owner: M1 (database / Admin).
 */
public final class BroadcastLogEntry {

    private final int logId;
    private final int alertId;
    private final String type;
    private final String severity;
    private final String source;
    private final String message;
    private final String broadcastStatus;
    private final int dashboardCount;
    private final long broadcastAtMillis;

    public BroadcastLogEntry(int logId, int alertId, String type, String severity, String source,
                             String message, String broadcastStatus, int dashboardCount,
                             long broadcastAtMillis) {
        this.logId = logId;
        this.alertId = alertId;
        this.type = type;
        this.severity = severity;
        this.source = source;
        this.message = message;
        this.broadcastStatus = broadcastStatus;
        this.dashboardCount = dashboardCount;
        this.broadcastAtMillis = broadcastAtMillis;
    }

    public int getLogId() {
        return logId;
    }

    public int getAlertId() {
        return alertId;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    /** The status that was pushed to the dashboards (e.g. "NEW" or "RESOLVED"). */
    public String getBroadcastStatus() {
        return broadcastStatus;
    }

    /** How many dashboards were connected when the alert was pushed (0 is possible). */
    public int getDashboardCount() {
        return dashboardCount;
    }

    public long getBroadcastAtMillis() {
        return broadcastAtMillis;
    }

    /** "yyyy-MM-dd HH:mm:ss" in local time. A new formatter per call: SimpleDateFormat is not thread-safe. */
    public String getFormattedBroadcastAt() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(broadcastAtMillis));
    }

    @Override
    public String toString() {
        return "BroadcastLogEntry[#" + logId + ", alert=" + alertId + ", " + type + ", "
                + broadcastStatus + ", dashboards=" + dashboardCount + "]";
    }
}

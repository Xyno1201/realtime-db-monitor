package dbmonitor.common;

import java.awt.Color;

/**
 * A row was deleted from a monitored table (detected by the DELETE trigger).
 * Database type string: {@value #TYPE_CODE}.
 *
 * Module owner: common (Builder A).
 */
public class RecordDeletedAlert extends Alert {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_CODE = "RECORD_DELETED";

    public RecordDeletedAlert(int id, Severity severity, String source, String message,
                              AlertStatus status, AlertStatus pushedStatus, long createdAtMillis)
            throws InvalidAlertException {
        super(id, severity, source, message, status, pushedStatus, createdAtMillis);
    }

    @Override
    public String getTypeCode() {
        return TYPE_CODE;
    }

    @Override
    public String getTypeLabel() {
        return "Record Deleted";
    }

    @Override
    public String getIconText() {
        return "[DEL]";
    }

    @Override
    public Color getColor() {
        return new Color(0xC62828); // red
    }

    @Override
    public String getDisplayMessage() {
        return "Record removed from " + getSource() + ": " + getMessage();
    }
}

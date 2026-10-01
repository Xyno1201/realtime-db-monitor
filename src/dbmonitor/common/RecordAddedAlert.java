package dbmonitor.common;

import java.awt.Color;

/**
 * A new row appeared in a monitored table (detected by the INSERT trigger).
 * Database type string: {@value #TYPE_CODE}.
 *
 * Module owner: common (Builder A).
 */
public class RecordAddedAlert extends Alert {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_CODE = "RECORD_ADDED";

    public RecordAddedAlert(int id, Severity severity, String source, String message,
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
        return "Record Added";
    }

    @Override
    public String getIconText() {
        return "[ADD]";
    }

    @Override
    public Color getColor() {
        return new Color(0x2E7D32); // green
    }

    @Override
    public String getDisplayMessage() {
        return "New record in " + getSource() + ": " + getMessage();
    }
}

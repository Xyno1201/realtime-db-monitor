package dbmonitor.common;

import java.awt.Color;

/**
 * An existing row of a monitored table was modified (detected by the UPDATE trigger) in a way
 * that matched at least one rule. Database type string: {@value #TYPE_CODE}.
 *
 * Module owner: common (Builder A).
 */
public class RecordChangedAlert extends Alert {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_CODE = "RECORD_CHANGED";

    public RecordChangedAlert(int id, Severity severity, String source, String message,
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
        return "Record Changed";
    }

    @Override
    public String getIconText() {
        return "[CHG]";
    }

    @Override
    public Color getColor() {
        return new Color(0x1565C0); // blue
    }

    @Override
    public String getDisplayMessage() {
        return "Change detected in " + getSource() + ": " + getMessage();
    }
}

package dbmonitor.common;

/**
 * Thrown when alert data is invalid: unknown type, unknown severity or status, or a source/message
 * that breaks the validation rules (master context v7, section 72).
 *
 * It is a CHECKED exception (extends Exception) on purpose: every caller can recover, so the
 * compiler forces each one to decide how -
 *   - Admin:  show the message in an error dialog, insert nothing;
 *   - DAO/poller: log once for that alert id, skip the row, continue (section 72.1).
 *
 * The message is written for humans, so it can be shown to the Admin user as it is.
 *
 * Module owner: M4 (common). Frozen once approved.
 */
public class InvalidAlertException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidAlertException(String message) {
        super(message);
    }

    public InvalidAlertException(String message, Throwable cause) {
        super(message, cause);
    }
}

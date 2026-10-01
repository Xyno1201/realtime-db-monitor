package dbmonitor.common;

import java.util.Locale;

/**
 * Lifecycle state of an alert (master context v7, sections 12-14):
 *
 * <pre>
 *   NEW --(poller broadcasts)--> SENT --(admin resolves)--> RESOLVED --(admin purges)--> row deleted
 *    |                                                          ^
 *    +-------------(admin resolves before first poll)-----------+
 * </pre>
 *
 * Used for both database columns:
 *   status        - the current state (never NULL)
 *   pushed_status - the last state successfully broadcast (NULL = never broadcast)
 *
 * Deliberately only three values. There is no INVALID or DELETED state (sections 13, 40).
 *
 * Module owner: M4 (common). Frozen once approved.
 */
public enum AlertStatus {
    NEW,
    SENT,
    RESOLVED;

    /**
     * Converts a non-null database value (the {@code status} column) into an AlertStatus.
     * Spaces and letter case are ignored.
     *
     * @throws InvalidAlertException if the value is null, empty, or not a known status
     */
    public static AlertStatus parse(String value) throws InvalidAlertException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidAlertException("Status is missing");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return AlertStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidAlertException("Unknown status '" + value.trim()
                    + "' (expected NEW, SENT or RESOLVED)", e);
        }
    }

    /**
     * Converts a value from the nullable {@code pushed_status} column.
     *
     * @return null if the value is SQL NULL (meaning "never broadcast"), otherwise the parsed status
     * @throws InvalidAlertException if the value is present but not a known status
     */
    public static AlertStatus parseNullable(String value) throws InvalidAlertException {
        if (value == null) {
            return null;
        }
        return parse(value);
    }
}

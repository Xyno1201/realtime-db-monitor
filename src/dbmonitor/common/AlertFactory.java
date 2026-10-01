package dbmonitor.common;

import java.util.Locale;

/**
 * The ONE place that maps a database type string to a concrete Alert subclass
 * (master context v7, sections 72 and 73).
 *
 *     "RECORD_ADDED"    -> RecordAddedAlert      (a row was inserted into a monitored table)
 *     "RECORD_CHANGED"  -> RecordChangedAlert    (a row was updated)
 *     "RECORD_DELETED"  -> RecordDeletedAlert    (a row was deleted)
 *     anything else     -> InvalidAlertException
 *
 * Everything outside this class works with {@code Alert} references only. Adding a new alert
 * type means: one new subclass + one new case here. Nothing else changes (Factory pattern).
 *
 * Two entry points:
 *   create(...)            - builds an Alert from a database row (used by AlertDAO).
 *                            An unknown type or invalid value throws InvalidAlertException; the
 *                            DAO then logs once for that id and skips the row (section 72.1).
 *   validateNewAlert(...)  - checks an alert BEFORE it is inserted (by the ChangeDetector),
 *                            when no id exists yet.
 *   typeForChange(...)     - which alert type a detected INSERT / UPDATE / DELETE becomes.
 *
 * Stateless and thread-safe (static methods, no mutable fields).
 *
 * Module owner: M4 (common). Frozen once approved.
 */
public final class AlertFactory {

    /** All supported database type strings. */
    private static final String[] KNOWN_TYPES = {
            RecordAddedAlert.TYPE_CODE,
            RecordChangedAlert.TYPE_CODE,
            RecordDeletedAlert.TYPE_CODE
    };

    private AlertFactory() {
        // static utility class - never instantiated
    }

    // =====================================================================
    // Building alerts from database rows
    // =====================================================================

    /**
     * Builds the correct Alert subclass from the raw column values of one {@code alerts} row.
     *
     * @param id              column id
     * @param type            column type (e.g. "RECORD_CHANGED")
     * @param severity        column severity (e.g. "HIGH")
     * @param source          column source
     * @param message         column message
     * @param status          column status (e.g. "NEW")
     * @param pushedStatus    column pushed_status; null means SQL NULL (never broadcast)
     * @param createdAtMillis column created_at, as milliseconds since 1970-01-01 UTC
     * @return a validated, immutable Alert of the matching subclass
     * @throws InvalidAlertException if the type is unknown or any value breaks a rule
     */
    public static Alert create(int id, String type, String severity, String source, String message,
                               String status, String pushedStatus, long createdAtMillis)
            throws InvalidAlertException {
        return create(id, type, Severity.parse(severity), source, message,
                AlertStatus.parse(status), AlertStatus.parseNullable(pushedStatus), createdAtMillis);
    }

    /**
     * Same as the String version, for callers that already hold enum values
     * (e.g. test harnesses or a dashboard demo mode).
     */
    public static Alert create(int id, String type, Severity severity, String source, String message,
                               AlertStatus status, AlertStatus pushedStatus, long createdAtMillis)
            throws InvalidAlertException {
        String code = normalizeType(type);

        if (RecordAddedAlert.TYPE_CODE.equals(code)) {
            return new RecordAddedAlert(id, severity, source, message, status, pushedStatus, createdAtMillis);
        }
        if (RecordChangedAlert.TYPE_CODE.equals(code)) {
            return new RecordChangedAlert(id, severity, source, message, status, pushedStatus, createdAtMillis);
        }
        if (RecordDeletedAlert.TYPE_CODE.equals(code)) {
            return new RecordDeletedAlert(id, severity, source, message, status, pushedStatus, createdAtMillis);
        }
        // normalizeType() already rejects unknown types; this line guards against a new entry in
        // KNOWN_TYPES that was added without a matching case above.
        throw new InvalidAlertException("No Alert subclass is mapped to type '" + code + "'");
    }

    // =====================================================================
    // Validation before INSERT (Admin input)
    // =====================================================================

    /**
     * Checks Admin input for a new alert, before anything is written to the database.
     * Rules (section 72): type must be known; severity must be present; source 1-50 and
     * message 1-255 characters after trimming.
     *
     * The DAO should then insert {@code normalizeType(type)} and the trimmed source/message.
     *
     * @throws InvalidAlertException with a message suitable for an error dialog
     */
    public static void validateNewAlert(String type, Severity severity, String source, String message)
            throws InvalidAlertException {
        normalizeType(type);
        if (severity == null) {
            throw new InvalidAlertException("Severity is missing");
        }
        Alert.requireValidSource(source);
        Alert.requireValidMessage(message);
    }

    // =====================================================================
    // Type helpers
    // =====================================================================

    /**
     * Returns the canonical database type string (trimmed, upper case).
     *
     * @throws InvalidAlertException if the type is missing or not one of the known types
     */
    public static String normalizeType(String type) throws InvalidAlertException {
        if (type == null || type.trim().isEmpty()) {
            throw new InvalidAlertException("Alert type is missing");
        }
        String code = type.trim().toUpperCase(Locale.ROOT);
        for (String known : KNOWN_TYPES) {
            if (known.equals(code)) {
                return known;
            }
        }
        throw new InvalidAlertException("Unknown alert type '" + type.trim()
                + "' (expected RECORD_ADDED, RECORD_CHANGED or RECORD_DELETED)");
    }

    /**
     * The alert type for a detected change: "INSERT" -> RECORD_ADDED, "UPDATE" -> RECORD_CHANGED,
     * "DELETE" -> RECORD_DELETED.
     *
     * @throws InvalidAlertException for any other change type
     */
    public static String typeForChange(String changeType) throws InvalidAlertException {
        String t = changeType == null ? "" : changeType.trim().toUpperCase(Locale.ROOT);
        if ("INSERT".equals(t)) {
            return RecordAddedAlert.TYPE_CODE;
        }
        if ("UPDATE".equals(t)) {
            return RecordChangedAlert.TYPE_CODE;
        }
        if ("DELETE".equals(t)) {
            return RecordDeletedAlert.TYPE_CODE;
        }
        throw new InvalidAlertException("Unknown change type '" + changeType + "'");
    }

    /** @return true if {@code type} is a supported database type string (spaces/case ignored) */
    public static boolean isKnownType(String type) {
        try {
            normalizeType(type);
            return true;
        } catch (InvalidAlertException e) {
            return false; // an unknown type is an expected answer here, not an error to report
        }
    }

    /** @return a copy of the supported type strings (a copy, so callers cannot change the list) */
    public static String[] getKnownTypes() {
        return KNOWN_TYPES.clone();
    }
}

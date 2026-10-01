package dbmonitor.common;

import java.awt.Color;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Base class of the alert hierarchy (master context v7, sections 11 and 73).
 *
 * <pre>
 *                    Alert  (abstract, Serializable)
 *          +-----------------+-----------------------+
 *   RecordAddedAlert   RecordChangedAlert   RecordDeletedAlert
 *   (row inserted)     (row updated)        (row deleted)       - in a monitored table
 * </pre>
 *
 * One Alert object = one row of the {@code alerts} table at the moment it was read.
 *
 * POLYMORPHISM: the poller, the TCP server, the client handlers and the dashboard only ever hold
 * {@code Alert} references. The subclass decides - at runtime - the type code, label, icon text,
 * colour and display message. No code outside AlertFactory needs to know which subclass it has.
 *
 * SERIALIZATION: objects travel from the server to the dashboards through ObjectOutputStream, so
 * the class is Serializable with an explicit serialVersionUID. Every field is itself serializable
 * (int, long, String, enums).
 *
 * IMMUTABILITY (deliberately no setters): after a broadcast, the SAME Alert object sits in the
 * outgoing queues of several ClientHandlers and is serialized by several threads at once. If it
 * could be modified, a dashboard might receive a half-changed alert. Final fields make that
 * impossible and need no locking. A status change is represented by a NEW Alert object built
 * from the updated database row - which is exactly what the poller produces on its next cycle.
 *
 * VALIDATION: the constructor enforces the rules of section 72, so an invalid Alert can never
 * exist. Rules: id > 0; severity and status present; source 1-50 and message 1-255 characters
 * after trimming. Values are stored trimmed.
 *
 * Module owner: M4 (common). Frozen once approved - announce any change to the whole team.
 */
public abstract class Alert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Maximum source length after trimming (matches VARCHAR(50) in sql/setup.sql). */
    public static final int MAX_SOURCE_LENGTH = 50;

    /** Maximum message length after trimming (matches VARCHAR(255) in sql/setup.sql). */
    public static final int MAX_MESSAGE_LENGTH = 255;

    private final int id;
    private final Severity severity;
    private final String source;
    private final String message;
    private final AlertStatus status;
    private final AlertStatus pushedStatus;   // null = never broadcast (SQL NULL)
    private final long createdAtMillis;

    /**
     * @param id              database id, must be greater than 0
     * @param severity        required
     * @param source          required, 1-50 characters after trimming
     * @param message         required, 1-255 characters after trimming
     * @param status          required (column {@code status})
     * @param pushedStatus    may be null (column {@code pushed_status})
     * @param createdAtMillis creation time, milliseconds since 1970-01-01 UTC
     * @throws InvalidAlertException if any rule above is broken
     */
    protected Alert(int id, Severity severity, String source, String message,
                    AlertStatus status, AlertStatus pushedStatus, long createdAtMillis)
            throws InvalidAlertException {
        if (id <= 0) {
            throw new InvalidAlertException("Alert id must be greater than 0 (got " + id + ")");
        }
        if (severity == null) {
            throw new InvalidAlertException("Severity is missing");
        }
        if (status == null) {
            throw new InvalidAlertException("Status is missing");
        }
        this.id = id;
        this.severity = severity;
        this.source = requireValidSource(source);
        this.message = requireValidMessage(message);
        this.status = status;
        this.pushedStatus = pushedStatus;
        this.createdAtMillis = createdAtMillis;
    }

    // =====================================================================
    // Abstract presentation methods - every subclass MUST provide these.
    // =====================================================================

    /** Exact string stored in the database column {@code type}, e.g. "RECORD_CHANGED". */
    public abstract String getTypeCode();

    /** Short human-readable type name for cards and tables, e.g. "Record Changed". */
    public abstract String getTypeLabel();

    /**
     * Plain-ASCII icon text for the card, e.g. "[CHG]". Text instead of an image or emoji keeps
     * the common model free of GUI resources and renders on every OS font.
     */
    public abstract String getIconText();

    /** Accent colour identifying this alert type on the dashboard. */
    public abstract Color getColor();

    /** Full sentence shown on the dashboard card, worded for this type of alert. */
    public abstract String getDisplayMessage();

    // =====================================================================
    // Shared behaviour
    // =====================================================================

    /** True when the alert's current status is RESOLVED (dashboard shows the card grey). */
    public boolean isResolved() {
        return status == AlertStatus.RESOLVED;
    }

    /** True if this row had been broadcast at least once when it was read (pushed_status not NULL). */
    public boolean hasBeenPushed() {
        return pushedStatus != null;
    }

    /** Creation time as a new Date object (a copy, so callers cannot change this alert). */
    public Date getCreatedAt() {
        return new Date(createdAtMillis);
    }

    /**
     * Creation time as "yyyy-MM-dd HH:mm:ss" in the machine's local time zone.
     * A new SimpleDateFormat per call, because SimpleDateFormat is not thread-safe.
     */
    public String getFormattedCreatedAt() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(createdAtMillis));
    }

    // =====================================================================
    // Getters (no setters - see IMMUTABILITY above)
    // =====================================================================

    /** Database id. Dashboards use it as the key for de-duplication (section 28). */
    public int getId() {
        return id;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public AlertStatus getStatus() {
        return status;
    }

    /** @return the last broadcast status, or null if the alert had never been broadcast */
    public AlertStatus getPushedStatus() {
        return pushedStatus;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id
                + ", type=" + getTypeCode()
                + ", severity=" + severity
                + ", status=" + status
                + ", pushedStatus=" + (pushedStatus == null ? "NULL" : pushedStatus.name())
                + ", source=" + source
                + ", message=" + message + "]";
    }

    // =====================================================================
    // Validation rules (section 72) - also used by AlertFactory.validateNewAlert()
    // before an INSERT, so the Admin form and the database rows follow the same rules.
    // =====================================================================

    /**
     * @return the trimmed source
     * @throws InvalidAlertException if null, empty after trimming, or longer than 50 characters
     */
    static String requireValidSource(String source) throws InvalidAlertException {
        return requireText("Source", source, MAX_SOURCE_LENGTH);
    }

    /**
     * @return the trimmed message
     * @throws InvalidAlertException if null, empty after trimming, or longer than 255 characters
     */
    static String requireValidMessage(String message) throws InvalidAlertException {
        return requireText("Message", message, MAX_MESSAGE_LENGTH);
    }

    private static String requireText(String fieldName, String value, int maxLength)
            throws InvalidAlertException {
        if (value == null) {
            throw new InvalidAlertException(fieldName + " is missing");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidAlertException(fieldName + " must not be empty");
        }
        if (trimmed.length() > maxLength) {
            throw new InvalidAlertException(fieldName + " is too long: " + trimmed.length()
                    + " characters (maximum " + maxLength + ")");
        }
        return trimmed;
    }
}

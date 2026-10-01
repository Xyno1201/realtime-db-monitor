package dbmonitor.common;

import java.awt.Color;
import java.util.Locale;

/**
 * How serious an alert is. Stored in the database column {@code severity} as the constant's
 * name ("LOW", "MEDIUM", "HIGH", "CRITICAL"); the schema requires the column to match this enum.
 *
 * Declared from least to most severe, so ordinal() and compareTo() give the severity order.
 * The dashboard's counters use an EnumMap keyed by this enum (section 87: "EnumMap is appropriate
 * for severity-keyed counters").
 *
 * Module owner: M4 (common). Frozen once approved.
 */
public enum Severity {

    LOW("Low", new Color(0x546E7A)),        // blue-grey
    MEDIUM("Medium", new Color(0xF9A825)),  // amber
    HIGH("High", new Color(0xEF6C00)),      // orange
    CRITICAL("Critical", new Color(0xC62828)); // red

    private final String label;
    private final Color color;

    Severity(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    /** Human-readable name for the GUI, e.g. "Critical". */
    public String getLabel() {
        return label;
    }

    /** Colour for severity indicators and counters. (java.awt.Color is Serializable.) */
    public Color getColor() {
        return color;
    }

    /**
     * Converts a database or form value into a Severity.
     * Leading/trailing spaces and letter case are ignored ("critical " -> CRITICAL).
     *
     * @throws InvalidAlertException if the value is null, empty, or not a known severity
     */
    public static Severity parse(String value) throws InvalidAlertException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidAlertException("Severity is missing");
        }
        // Locale.ROOT: toUpperCase must not depend on the machine's language settings
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Severity.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidAlertException("Unknown severity '" + value.trim()
                    + "' (expected LOW, MEDIUM, HIGH or CRITICAL)", e);
        }
    }
}

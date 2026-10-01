package dbmonitor.rules;

import java.util.Locale;

/**
 * The kind of change a trigger recorded in data_changes.change_type.
 *
 * Module owner: rules engine (Builder A).
 */
public enum ChangeType {
    INSERT,
    UPDATE,
    DELETE;

    /** @return the matching ChangeType, or null if the text is not one of the three */
    public static ChangeType parseOrNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        for (ChangeType t : values()) {
            if (t.name().equals(v)) {
                return t;
            }
        }
        return null;
    }
}

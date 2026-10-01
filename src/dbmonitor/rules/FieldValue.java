package dbmonitor.rules;

import java.math.BigDecimal;

/**
 * One column of a changed row: its value before and after the change, exactly as the trigger
 * stored them in data_change_values (as text).
 *
 *   INSERT -> oldValue is null      DELETE -> newValue is null
 *
 * Immutable. Numbers are parsed on demand; a value that is not a number simply gives null, so a
 * numeric rule pointed at a text column never matches instead of crashing.
 *
 * Module owner: rules engine (Builder A).
 */
public final class FieldValue {

    private final String field;
    private final String oldValue;
    private final String newValue;

    public FieldValue(String field, String oldValue, String newValue) {
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    /** True if old and new differ (null-safe). */
    public boolean isChanged() {
        return oldValue == null ? newValue != null : !oldValue.equals(newValue);
    }

    /** @return the old value as a number, or null if absent or not numeric */
    public BigDecimal oldNumber() {
        return toNumber(oldValue);
    }

    /** @return the new value as a number, or null if absent or not numeric */
    public BigDecimal newNumber() {
        return toNumber(newValue);
    }

    private static BigDecimal toNumber(String text) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;   // not a number: numeric rules treat this field as "no value"
        }
    }

    @Override
    public String toString() {
        return field + ": " + oldValue + " -> " + newValue;
    }
}

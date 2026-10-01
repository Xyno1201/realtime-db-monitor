package dbmonitor.rules;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One change recorded by a trigger: a row of data_changes plus its data_change_values.
 *
 * Immutable - built once by ChangeDAO, then read by the rules engine (server) or shown in the
 * Admin's Changes tab. The field map keeps column order and cannot be modified.
 *
 * Module owner: rules engine (Builder A).
 */
public final class DataChange {

    private final int changeId;
    private final String tableName;
    private final int rowId;
    private final ChangeType type;
    private final String changedBy;
    private final long changedAtMillis;
    private final Map<String, FieldValue> fields;
    private final long processedAtMillis;   // 0 = not evaluated yet
    private final String outcome;           // null until evaluated

    public DataChange(int changeId, String tableName, int rowId, ChangeType type, String changedBy,
                      long changedAtMillis, Map<String, FieldValue> fields,
                      long processedAtMillis, String outcome) {
        this.changeId = changeId;
        this.tableName = tableName;
        this.rowId = rowId;
        this.type = type;
        this.changedBy = changedBy;
        this.changedAtMillis = changedAtMillis;
        // defensive copy + read-only view: nobody can change a recorded change afterwards
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, FieldValue>(fields));
        this.processedAtMillis = processedAtMillis;
        this.outcome = outcome;
    }

    public int getChangeId() {
        return changeId;
    }

    public String getTableName() {
        return tableName;
    }

    public int getRowId() {
        return rowId;
    }

    public ChangeType getType() {
        return type;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public long getChangedAtMillis() {
        return changedAtMillis;
    }

    public Map<String, FieldValue> getFields() {
        return fields;
    }

    /** @return the column's values, or null if the trigger did not record that column */
    public FieldValue field(String name) {
        return fields.get(name);
    }

    public boolean isProcessed() {
        return processedAtMillis > 0;
    }

    public String getOutcome() {
        return outcome;
    }

    /**
     * A human name for the row, taken from {@code labelField} (e.g. the product name):
     * the new value, or the old value for a DELETE. Falls back to "#id".
     */
    public String label(String labelField) {
        if (labelField != null) {
            FieldValue v = fields.get(labelField);
            if (v != null) {
                String text = v.getNewValue() != null ? v.getNewValue() : v.getOldValue();
                if (text != null && !text.trim().isEmpty()) {
                    return "'" + text.trim() + "'";
                }
            }
        }
        return "#" + rowId;
    }

    /** "price: 499.00 -> 49.00, stock: 18 -> 0" - only the columns that changed (for UPDATE). */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (FieldValue v : fields.values()) {
            String part;
            if (type == ChangeType.INSERT) {
                part = v.getField() + "=" + v.getNewValue();
            } else if (type == ChangeType.DELETE) {
                part = v.getField() + "=" + v.getOldValue();
            } else if (v.isChanged()) {
                part = v.getField() + ": " + v.getOldValue() + " -> " + v.getNewValue();
            } else {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    public String getFormattedChangedAt() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(changedAtMillis));
    }

    @Override
    public String toString() {
        return "DataChange[#" + changeId + " " + type + " " + tableName + " #" + rowId + " by " + changedBy
                + " {" + summary() + "}]";
    }
}

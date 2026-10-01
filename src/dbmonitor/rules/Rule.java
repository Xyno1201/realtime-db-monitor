package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Base class of every alert rule (INHERITANCE + POLYMORPHISM).
 *
 * <pre>
 *                              Rule (abstract)
 *     +-----------+-----------+--------+-----------+-----------+-----------+
 *  RecordInserted RecordDeleted FieldChanged PercentDrop PercentRise Threshold Delta
 * </pre>
 *
 * The concrete rules for the products table are created in ProductRules. The rules engine
 * (RuleSet) holds a List&lt;Rule&gt; and calls
 * evaluate() on each one without knowing which subclass it is.
 *
 * Subclasses only implement check(): "does this change break my rule? if yes, say why".
 *
 * Immutable and stateless, so one RuleSet can be shared safely between threads.
 *
 * Module owner: rules engine (Builder A).
 */
public abstract class Rule {

    /** Table name meaning "any monitored table". */
    public static final String ANY_TABLE = "*";

    private final String id;
    private final String table;
    private final Severity severity;
    private final String description;

    protected Rule(String id, String table, Severity severity, String description) {
        this.id = id;
        this.table = table;
        this.severity = severity;
        this.description = description;
    }

    /**
     * @return a short reason such as "price 499.00 -> 49.00 (-90.2%)" if the change breaks this rule,
     *         or null if it does not (or the rule is for another table)
     */
    public final String evaluate(DataChange change) {
        if (!ANY_TABLE.equals(table) && !table.equalsIgnoreCase(change.getTableName())) {
            return null;
        }
        return check(change);
    }

    /** The rule's own test. Called only for changes on this rule's table. */
    protected abstract String check(DataChange change);

    /** Short name of the rule type, shown in the Admin's Rules tab, e.g. "drop_pct". */
    public abstract String getTypeKeyword();

    /** Human-readable condition for the Admin's Rules tab, e.g. "price drops by >= 50%". */
    public abstract String describeCondition();

    public String getId() {
        return id;
    }

    public String getTable() {
        return table;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return id + " [" + getTypeKeyword() + ", " + table + ", " + severity + "] " + describeCondition();
    }

    // ---------------------------------------------------------------- helpers for subclasses

    /** Percentage change from old to new, e.g. 499 -> 49 gives -90.18...; null if old is 0 or missing. */
    protected static BigDecimal percentChange(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null || oldValue.signum() == 0) {
            return null;
        }
        return newValue.subtract(oldValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(oldValue.abs(), 4, RoundingMode.HALF_UP);
    }

    protected static String formatPercent(BigDecimal pct) {
        return String.format(Locale.ROOT, "%+.1f%%", pct.doubleValue());
    }

    /** Plain number text for rule descriptions (no scientific notation, no trailing zeros). */
    protected static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

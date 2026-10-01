package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.math.BigDecimal;

/**
 * Rule type {@code threshold}: a numeric column's NEW value compares against a fixed value.
 * Applies to INSERT and UPDATE.
 *
 *   rule.R7.type     = threshold
 *   rule.R7.field    = stock
 *   rule.R7.operator = <          one of  <  <=  >  >=  ==  !=
 *   rule.R7.value    = 10
 *   rule.R7.crossing = true        (default true)
 *
 * crossing = true: on an UPDATE the rule fires only when the value CROSSES the line (the old value
 * did not meet the condition, the new one does). So "stock below 10" fires once at 12 -> 8, not
 * again at 8 -> 7. That is what stops one low-stock product from flooding the dashboards.
 * crossing = false: fires on every change whose new value meets the condition.
 *
 * Module owner: rules engine (Builder A).
 */
public class ThresholdRule extends Rule {

    public static final String KEYWORD = "threshold";

    private final String field;
    private final String operator;
    private final BigDecimal value;
    private final boolean crossing;

    public ThresholdRule(String id, String table, Severity severity, String description,
                         String field, String operator, BigDecimal value, boolean crossing) {
        super(id, table, severity, description);
        if (!isValidOperator(operator)) {
            throw new IllegalArgumentException("unknown operator '" + operator + "'");
        }
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.crossing = crossing;
    }

    /** The comparison operators a threshold rule accepts. */
    public static boolean isValidOperator(String op) {
        return "<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)
                || "==".equals(op) || "!=".equals(op);
    }

    @Override
    protected String check(DataChange change) {
        if (change.getType() == ChangeType.DELETE) {
            return null;
        }
        FieldValue v = change.field(field);
        if (v == null) {
            return null;
        }
        BigDecimal newNumber = v.newNumber();
        if (newNumber == null || !meets(newNumber)) {
            return null;
        }
        if (change.getType() == ChangeType.UPDATE) {
            if (!v.isChanged()) {
                return null;   // this column did not change in this update
            }
            BigDecimal oldNumber = v.oldNumber();
            if (crossing && oldNumber != null && meets(oldNumber)) {
                return null;   // it was already past the line - don't alert again
            }
            return field + " " + v.getOldValue() + " -> " + v.getNewValue() + " (" + operator + " " + plain(value) + ")";
        }
        return field + " = " + v.getNewValue() + " (" + operator + " " + plain(value) + ")";
    }

    private boolean meets(BigDecimal x) {
        int c = x.compareTo(value);
        if ("<".equals(operator)) {
            return c < 0;
        } else if ("<=".equals(operator)) {
            return c <= 0;
        } else if (">".equals(operator)) {
            return c > 0;
        } else if (">=".equals(operator)) {
            return c >= 0;
        } else if ("==".equals(operator)) {
            return c == 0;
        } else {
            return c != 0;
        }
    }

    @Override
    public String getTypeKeyword() {
        return KEYWORD;
    }

    @Override
    public String describeCondition() {
        return field + " " + operator + " " + plain(value) + (crossing ? " (when it crosses)" : "");
    }
}

package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.math.BigDecimal;

/**
 * Rule type {@code delta}: a numeric column changed by MORE than {@code value} units in one
 * UPDATE, in either direction (e.g. a suspicious bulk stock adjustment).
 *
 *   rule.R8.type  = delta
 *   rule.R8.field = stock
 *   rule.R8.value = 500
 *
 * Module owner: rules engine (Builder A).
 */
public class DeltaRule extends Rule {

    public static final String KEYWORD = "delta";

    private final String field;
    private final BigDecimal value;

    public DeltaRule(String id, String table, Severity severity, String description, String field, BigDecimal value) {
        super(id, table, severity, description);
        this.field = field;
        this.value = value;
    }

    @Override
    protected String check(DataChange change) {
        if (change.getType() != ChangeType.UPDATE) {
            return null;
        }
        FieldValue v = change.field(field);
        if (v == null || v.oldNumber() == null || v.newNumber() == null) {
            return null;
        }
        BigDecimal delta = v.newNumber().subtract(v.oldNumber());
        if (delta.abs().compareTo(value) > 0) {
            return field + " " + v.getOldValue() + " -> " + v.getNewValue()
                    + " (" + (delta.signum() > 0 ? "+" : "") + plain(delta) + ")";
        }
        return null;
    }

    @Override
    public String getTypeKeyword() {
        return KEYWORD;
    }

    @Override
    public String describeCondition() {
        return field + " changes by more than " + plain(value) + " in one edit";
    }
}

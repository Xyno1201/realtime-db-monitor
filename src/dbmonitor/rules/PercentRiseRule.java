package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.math.BigDecimal;

/**
 * Rule type {@code rise_pct}: a numeric column rose by at least {@code min} percent in one
 * UPDATE (optionally by less than {@code max} percent).
 *
 *   rule.R4.type  = rise_pct
 *   rule.R4.field = price
 *   rule.R4.min   = 100
 *
 * Module owner: rules engine (Builder A).
 */
public class PercentRiseRule extends Rule {

    public static final String KEYWORD = "rise_pct";

    private final String field;
    private final BigDecimal min;
    private final BigDecimal max;   // null = no upper limit

    public PercentRiseRule(String id, String table, Severity severity, String description,
                           String field, BigDecimal min, BigDecimal max) {
        super(id, table, severity, description);
        this.field = field;
        this.min = min;
        this.max = max;
    }

    @Override
    protected String check(DataChange change) {
        if (change.getType() != ChangeType.UPDATE) {
            return null;
        }
        FieldValue v = change.field(field);
        if (v == null) {
            return null;
        }
        BigDecimal pct = percentChange(v.oldNumber(), v.newNumber());
        if (pct == null || pct.signum() <= 0) {
            return null;
        }
        boolean aboveMin = pct.compareTo(min) >= 0;
        boolean belowMax = max == null || pct.compareTo(max) < 0;
        if (aboveMin && belowMax) {
            return field + " " + v.getOldValue() + " -> " + v.getNewValue() + " (" + formatPercent(pct) + ")";
        }
        return null;
    }

    @Override
    public String getTypeKeyword() {
        return KEYWORD;
    }

    @Override
    public String describeCondition() {
        return field + " rises by >= " + plain(min) + "%" + (max == null ? "" : " and < " + plain(max) + "%");
    }
}

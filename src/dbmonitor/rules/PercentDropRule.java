package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.math.BigDecimal;

/**
 * Rule type {@code drop_pct}: a numeric column fell by at least {@code min} percent in one
 * UPDATE (and, if {@code max} is given, by less than {@code max} percent - used for bands such as
 * "20-50% drop = HIGH" next to "50%+ drop = CRITICAL").
 *
 *   rule.R3.type  = drop_pct
 *   rule.R3.field = price
 *   rule.R3.min   = 20
 *   rule.R3.max   = 50
 *
 * Module owner: rules engine (Builder A).
 */
public class PercentDropRule extends Rule {

    public static final String KEYWORD = "drop_pct";

    private final String field;
    private final BigDecimal min;
    private final BigDecimal max;   // null = no upper limit

    public PercentDropRule(String id, String table, Severity severity, String description,
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
        if (pct == null || pct.signum() >= 0) {
            return null;   // not a drop (or old value 0 / not numeric)
        }
        BigDecimal drop = pct.negate();
        boolean aboveMin = drop.compareTo(min) >= 0;
        boolean belowMax = max == null || drop.compareTo(max) < 0;
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
        return field + " drops by >= " + plain(min) + "%" + (max == null ? "" : " and < " + plain(max) + "%");
    }
}

package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rule type {@code changed}: any of the listed columns changed in an UPDATE.
 *
 *   rule.R10.type   = changed
 *   rule.R10.fields = name, category
 *
 * Module owner: rules engine (Builder A).
 */
public class FieldChangedRule extends Rule {

    public static final String KEYWORD = "changed";

    private final List<String> fields;

    public FieldChangedRule(String id, String table, Severity severity, String description, List<String> fields) {
        super(id, table, severity, description);
        this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
    }

    @Override
    protected String check(DataChange change) {
        if (change.getType() != ChangeType.UPDATE) {
            return null;
        }
        StringBuilder reason = new StringBuilder();
        for (String f : fields) {
            FieldValue v = change.field(f);
            if (v != null && v.isChanged()) {
                if (reason.length() > 0) {
                    reason.append(", ");
                }
                reason.append(f).append(" '").append(v.getOldValue()).append("' -> '").append(v.getNewValue()).append("'");
            }
        }
        return reason.length() == 0 ? null : reason.toString();
    }

    public List<String> getFields() {
        return fields;
    }

    @Override
    public String getTypeKeyword() {
        return KEYWORD;
    }

    @Override
    public String describeCondition() {
        return "any of [" + joinFields() + "] changes";
    }

    private String joinFields() {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(f);
        }
        return sb.toString();
    }
}

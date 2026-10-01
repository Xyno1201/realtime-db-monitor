package dbmonitor.rules;

import dbmonitor.common.Severity;

/**
 * Rule type {@code deleted}: a row was removed from the table.
 *
 * Module owner: rules engine (Builder A).
 */
public class RecordDeletedRule extends Rule {

    public static final String KEYWORD = "deleted";

    public RecordDeletedRule(String id, String table, Severity severity, String description) {
        super(id, table, severity, description);
    }

    @Override
    protected String check(DataChange change) {
        return change.getType() == ChangeType.DELETE ? "record deleted" : null;
    }

    @Override
    public String getTypeKeyword() {
        return KEYWORD;
    }

    @Override
    public String describeCondition() {
        return "a row is deleted";
    }
}

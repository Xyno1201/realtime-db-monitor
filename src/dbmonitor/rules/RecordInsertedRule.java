package dbmonitor.rules;

import dbmonitor.common.Severity;

/**
 * Rule type {@code inserted}: a new row was added to the table.
 *
 * Module owner: rules engine (Builder A).
 */
public class RecordInsertedRule extends Rule {

    public static final String KEYWORD = "inserted";

    public RecordInsertedRule(String id, String table, Severity severity, String description) {
        super(id, table, severity, description);
    }

    @Override
    protected String check(DataChange change) {
        return change.getType() == ChangeType.INSERT ? "new record added" : null;
    }

    @Override
    public String getTypeKeyword() {
        return KEYWORD;
    }

    @Override
    public String describeCondition() {
        return "a row is inserted";
    }
}

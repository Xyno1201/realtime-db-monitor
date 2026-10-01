package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The active rules (see ProductRules), plus which column names a row in alert messages.
 *
 * evaluate() runs EVERY rule on a change (polymorphism: each Rule subclass checks in its own way)
 * and combines the results: all reasons are kept, and the alert severity is the highest one.
 *
 * Immutable, so the server thread and the Admin can share it safely.
 *
 * Module owner: rules engine (Builder A).
 */
public final class RuleSet {

    private final List<Rule> rules;
    private final Map<String, String> labelFields;   // table -> column used to name a row

    public RuleSet(List<Rule> rules, Map<String, String> labelFields) {
        this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
        this.labelFields = Collections.unmodifiableMap(new HashMap<String, String>(labelFields));
    }

    /** Runs all rules on one change. */
    public DetectionResult evaluate(DataChange change) {
        List<String> reasons = new ArrayList<String>();
        List<String> ids = new ArrayList<String>();
        Severity highest = null;
        for (Rule rule : rules) {                 // only the Rule type is used here - no instanceof
            String reason = rule.evaluate(change);
            if (reason != null) {
                reasons.add("[" + rule.getId() + "] " + reason);
                ids.add(rule.getId());
                if (highest == null || rule.getSeverity().compareTo(highest) > 0) {
                    highest = rule.getSeverity();   // enum order: LOW < MEDIUM < HIGH < CRITICAL
                }
            }
        }
        return new DetectionResult(reasons, ids, highest);
    }

    /** @return the column that names rows of this table in messages, or null */
    public String labelFieldFor(String table) {
        return labelFields.get(table.toLowerCase());
    }

    public List<Rule> getRules() {
        return rules;
    }

    @Override
    public String toString() {
        return rules.size() + " rule(s)";
    }
}

package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the rules decided about one change: which rules matched (with reasons) and the overall
 * severity - the HIGHEST severity among the matched rules. No match = no alert.
 *
 * Immutable.
 *
 * Module owner: rules engine (Builder A).
 */
public final class DetectionResult {

    private final List<String> reasons;       // e.g. "[R2] price 499.00 -> 49.00 (-90.2%)"
    private final List<String> matchedRuleIds;
    private final Severity severity;          // null if nothing matched

    public DetectionResult(List<String> reasons, List<String> matchedRuleIds, Severity severity) {
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(reasons));
        this.matchedRuleIds = Collections.unmodifiableList(new ArrayList<String>(matchedRuleIds));
        this.severity = severity;
    }

    public boolean isAlert() {
        return severity != null;
    }

    public Severity getSeverity() {
        return severity;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public List<String> getMatchedRuleIds() {
        return matchedRuleIds;
    }

    /** All reasons joined with "; ". */
    public String reasonsText() {
        StringBuilder sb = new StringBuilder();
        for (String r : reasons) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(r);
        }
        return sb.toString();
    }
}

package dbmonitor.rules;

import dbmonitor.common.Severity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The alert rules for the monitored table "products" (R1-R10), written in Java.
 *
 * To change a rule: edit the line below, rebuild (build.cmd) and restart the server.
 * Each line creates one object of a Rule subclass - the rules engine (RuleSet) then treats them
 * all as plain Rule objects (polymorphism).
 *
 *   Rule  Fires when                                   Severity
 *   R1    price set to 0 or below                      CRITICAL
 *   R2    price drops by 50% or more                   CRITICAL
 *   R3    price drops by 20% - 50%                     HIGH
 *   R4    price rises by 100% or more (doubled)        MEDIUM
 *   R5    product deleted                              HIGH
 *   R6    stock falls to 0                             HIGH
 *   R7    stock falls below 10                         MEDIUM
 *   R8    stock changes by more than 500 in one edit   MEDIUM
 *   R9    new product added                            LOW
 *   R10   name or category changed                     LOW
 *
 * Threshold rules (R1, R6, R7) fire only when the value CROSSES the line: stock 5 -> 3 does not
 * alert again. One change can match several rules (price -> 0 matches R1 and R2); it still becomes
 * ONE alert with the highest severity.
 *
 * Module owner: rules engine (Builder A).
 */
public final class ProductRules {

    /** The monitored table (must match the triggers in sql/setup.sql). */
    public static final String TABLE = "products";

    /** The column used to name a product in alert messages: 'Desk Lamp' instead of #19. */
    public static final String LABEL_FIELD = "name";

    private ProductRules() {
    }

    /** @return a new, immutable set of the 10 product rules */
    public static RuleSet create() {
        List<Rule> rules = new ArrayList<Rule>();

        // ---- price ----
        rules.add(new ThresholdRule("R1", TABLE, Severity.CRITICAL, "Price set to zero or below",
                "price", "<=", num("0"), true));
        rules.add(new PercentDropRule("R2", TABLE, Severity.CRITICAL, "Price dropped by 50% or more",
                "price", num("50"), null));
        rules.add(new PercentDropRule("R3", TABLE, Severity.HIGH, "Price dropped by 20-50%",
                "price", num("20"), num("50")));
        rules.add(new PercentRiseRule("R4", TABLE, Severity.MEDIUM, "Price doubled or more",
                "price", num("100"), null));

        // ---- whole product ----
        rules.add(new RecordDeletedRule("R5", TABLE, Severity.HIGH, "Product deleted"));

        // ---- stock ----
        rules.add(new ThresholdRule("R6", TABLE, Severity.HIGH, "Out of stock",
                "stock", "<=", num("0"), true));
        rules.add(new ThresholdRule("R7", TABLE, Severity.MEDIUM, "Low stock (below 10)",
                "stock", "<", num("10"), true));
        rules.add(new DeltaRule("R8", TABLE, Severity.MEDIUM, "Bulk stock adjustment (more than 500 units in one edit)",
                "stock", num("500")));

        // ---- new product / identity ----
        rules.add(new RecordInsertedRule("R9", TABLE, Severity.LOW, "New product added"));
        rules.add(new FieldChangedRule("R10", TABLE, Severity.LOW, "Name or category changed",
                Arrays.asList("name", "category")));

        Map<String, String> labels = new HashMap<String, String>();
        labels.put(TABLE, LABEL_FIELD);
        return new RuleSet(rules, labels);
    }

    private static BigDecimal num(String text) {
        return new BigDecimal(text);
    }
}

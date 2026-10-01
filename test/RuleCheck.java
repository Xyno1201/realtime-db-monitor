import dbmonitor.common.Severity;
import dbmonitor.rules.ChangeType;
import dbmonitor.rules.DataChange;
import dbmonitor.rules.DetectionResult;
import dbmonitor.rules.FieldValue;
import dbmonitor.rules.ProductRules;
import dbmonitor.rules.Rule;
import dbmonitor.rules.RuleSet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * THROWAWAY HARNESS for the v8 rules engine. No database and no server needed.
 *
 * Part A - the hard-coded product rules (ProductRules): R1..R10, right types and severities.
 * Part B - feeds made-up changes (like the triggers would record) through the rules and checks
 *          which rules match and which severity the alert would get: R1..R10, combined, no-match.
 * Part C - the rule set cannot be changed while the server is using it (immutable).
 *
 * Run from the project root:  java -cp "out;lib/*" RuleCheck   (':' on Mac/Linux)
 * Exit code 0 = all checks passed, 1 = at least one FAIL.
 */
public class RuleCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Part A: the product rules ===");
        RuleSet rules = partA();

        System.out.println();
        System.out.println("=== Part B: evaluate sample changes ===");
        partB(rules);

        System.out.println();
        System.out.println("=== Part C: the rule set is read-only ===");
        partC(rules);

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================

    private static RuleSet partA() {
        RuleSet rules = ProductRules.create();
        for (Rule r : rules.getRules()) {
            System.out.println("      " + r.getId() + "  " + r.getSeverity() + "  " + r.getTypeKeyword()
                    + "  " + r.describeCondition());
        }
        check(rules.getRules().size() == 10, "10 product rules (found " + rules.getRules().size() + ")");
        boolean inOrder = true;
        for (int i = 0; i < rules.getRules().size(); i++) {
            if (!("R" + (i + 1)).equals(rules.getRules().get(i).getId())) {
                inOrder = false;
            }
        }
        check(inOrder, "rule ids are R1..R10, in order");
        check("name".equals(rules.labelFieldFor("products")), "products are named by their 'name' column in messages");
        Rule r1 = find(rules, "R1");
        check(r1 != null && r1.getSeverity() == Severity.CRITICAL && "threshold".equals(r1.getTypeKeyword()),
                "R1 is a CRITICAL threshold rule");
        Rule r5 = find(rules, "R5");
        check(r5 != null && r5.getSeverity() == Severity.HIGH && "deleted".equals(r5.getTypeKeyword()),
                "R5 is a HIGH deleted rule");
        boolean allProducts = true;
        for (Rule r : rules.getRules()) {
            if (!ProductRules.TABLE.equals(r.getTable())) {
                allProducts = false;
            }
        }
        check(allProducts, "every rule watches the products table");
        return rules;
    }

    // =====================================================================

    private static void partB(RuleSet rules) {
        // price rules
        // price set to 0 is ALSO a -100% drop: both rules match, one alert, CRITICAL
        expect(rules, update("price", "499.00", "0.00"), Severity.CRITICAL, "price set to 0", "R1", "R2");
        expect(rules, update("price", "499.00", "49.00"), Severity.CRITICAL, "price 499 -> 49 (-90%)", "R2");
        expect(rules, update("price", "100.00", "70.00"), Severity.HIGH, "price 100 -> 70 (-30%)", "R3");
        expect(rules, update("price", "100.00", "250.00"), Severity.MEDIUM, "price 100 -> 250 (+150%)", "R4");
        expectNone(rules, update("price", "100.00", "90.00"), "price 100 -> 90 (-10%) matches nothing");
        expect(rules, update("price", "100.00", "50.00"), Severity.CRITICAL,
                "price 100 -> 50 (exactly -50%) is R2, not R3", "R2");

        // delete / insert
        expect(rules, change(ChangeType.DELETE, product("Desk Lamp", "Home", "45.00", "30"), true),
                Severity.HIGH, "product deleted", "R5");
        expect(rules, change(ChangeType.INSERT, product("Webcam", "Electronics", "59.00", "40"), false),
                Severity.LOW, "new product added", "R9");

        // stock rules
        // stock 0 is ALSO below 10: R6 (HIGH) + R7 (MEDIUM) -> HIGH
        expect(rules, update("stock", "25", "0"), Severity.HIGH, "stock 25 -> 0", "R6", "R7");
        expect(rules, update("stock", "25", "5"), Severity.MEDIUM, "stock 25 -> 5", "R7");
        expectNone(rules, update("stock", "5", "3"), "stock 5 -> 3 does not alert again (already below 10)");
        expect(rules, update("stock", "40", "900"), Severity.MEDIUM, "stock 40 -> 900 (+860)", "R8");
        expectNone(rules, update("stock", "40", "540"), "stock +500 exactly is not 'more than 500'");

        // name / category
        expect(rules, update("category", "Home", "Garden"), Severity.LOW, "category changed", "R10");

        // one change, several rules -> ONE result with the highest severity
        Map<String, FieldValue> f = new LinkedHashMap<String, FieldValue>();
        f.put("name", new FieldValue("name", "Headphones", "Headphones X"));
        f.put("category", new FieldValue("category", "Electronics", "Electronics"));
        f.put("price", new FieldValue("price", "499.00", "49.00"));
        f.put("stock", new FieldValue("stock", "25", "0"));
        DetectionResult r = rules.evaluate(new DataChange(1, "products", 3, ChangeType.UPDATE, "root@localhost",
                System.currentTimeMillis(), f, 0L, null));
        System.out.println("      combined: " + r.getSeverity() + " " + r.reasonsText());
        check(r.isAlert() && r.getSeverity() == Severity.CRITICAL
                        && r.getMatchedRuleIds().contains("R2") && r.getMatchedRuleIds().contains("R6")
                        && r.getMatchedRuleIds().contains("R7") && r.getMatchedRuleIds().contains("R10")
                        && r.getMatchedRuleIds().size() == 4,
                "combined change matches R2+R6+R7+R10 -> ONE result, severity CRITICAL (highest)");

        // insert with low stock: R9 (LOW) + R7 (MEDIUM) -> MEDIUM
        DetectionResult ins = rules.evaluate(change(ChangeType.INSERT, product("Cable", "Electronics", "9.00", "3"), false));
        check(ins.getSeverity() == Severity.MEDIUM && ins.getMatchedRuleIds().contains("R9")
                && ins.getMatchedRuleIds().contains("R7"), "insert with stock 3 -> R9 + R7 -> MEDIUM");

        // rules only watch their own table
        DataChange other = new DataChange(2, "customers", 1, ChangeType.DELETE, "root@localhost",
                System.currentTimeMillis(), new LinkedHashMap<String, FieldValue>(), 0L, null);
        check(!rules.evaluate(other).isAlert(), "a delete in another table (customers) is ignored by products rules");

        // labels used in alert messages
        DataChange ch = update("price", "499.00", "49.00");
        check("'Headphones'".equals(ch.label(rules.labelFieldFor("products"))), "label(name) -> 'Headphones'");
    }

    // =====================================================================

    private static void partC(RuleSet rules) {
        try {
            rules.getRules().clear();
            check(false, "the rule list should be read-only");
        } catch (UnsupportedOperationException e) {
            check(true, "the rule list is read-only (it cannot be changed by mistake while in use)");
        }
        check(rules.getRules().size() == 10, "still 10 rules afterwards");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** An UPDATE of product #3 "Headphones" where only one field changes. */
    private static DataChange update(String field, String oldValue, String newValue) {
        Map<String, String[]> base = new LinkedHashMap<String, String[]>();
        base.put("name", new String[]{"Headphones", "Headphones"});
        base.put("category", new String[]{"Electronics", "Electronics"});
        base.put("price", new String[]{"499.00", "499.00"});
        base.put("stock", new String[]{"25", "25"});
        base.put(field, new String[]{oldValue, newValue});
        Map<String, FieldValue> f = new LinkedHashMap<String, FieldValue>();
        for (Map.Entry<String, String[]> e : base.entrySet()) {
            f.put(e.getKey(), new FieldValue(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        return new DataChange(1, "products", 3, ChangeType.UPDATE, "root@localhost",
                System.currentTimeMillis(), f, 0L, null);
    }

    private static String[] product(String name, String category, String price, String stock) {
        return new String[]{name, category, price, stock};
    }

    /** INSERT has only new values, DELETE only old values - like the triggers record them. */
    private static DataChange change(ChangeType type, String[] values, boolean valuesAreOld) {
        String[] names = {"name", "category", "price", "stock"};
        Map<String, FieldValue> f = new LinkedHashMap<String, FieldValue>();
        for (int i = 0; i < names.length; i++) {
            f.put(names[i], valuesAreOld ? new FieldValue(names[i], values[i], null)
                    : new FieldValue(names[i], null, values[i]));
        }
        return new DataChange(1, "products", 7, type, "root@localhost", System.currentTimeMillis(), f, 0L, null);
    }

    /** The change must match exactly these rules, and the alert gets this severity. */
    private static void expect(RuleSet rules, DataChange change, Severity severity, String what, String... ruleIds) {
        DetectionResult r = rules.evaluate(change);
        System.out.println("      " + what + " -> " + (r.isAlert() ? r.getSeverity() + " " + r.reasonsText() : "no alert"));
        check(r.isAlert() && r.getSeverity() == severity
                        && r.getMatchedRuleIds().equals(java.util.Arrays.asList(ruleIds)),
                what + " -> " + java.util.Arrays.toString(ruleIds) + ", " + severity);
    }

    private static void expectNone(RuleSet rules, DataChange change, String what) {
        DetectionResult r = rules.evaluate(change);
        if (r.isAlert()) {
            System.out.println("      unexpected: " + r.reasonsText());
        }
        check(!r.isAlert(), what);
    }

    private static Rule find(RuleSet rules, String id) {
        for (Rule r : rules.getRules()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private static void check(boolean ok, String description) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + description);
        } else {
            failed++;
            System.out.println("FAIL  " + description);
        }
    }
}

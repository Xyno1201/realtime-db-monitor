package dbmonitor.admin;

import dbmonitor.rules.RuleSet;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;

/**
 * "Rules" tab (read-only): the product rules R1-R10 the server uses to turn changes into alerts.
 *
 * The rules are defined in Java (dbmonitor.rules.ProductRules). This tab shows the same RuleSet the
 * server builds, so what you see here is exactly what the server checks.
 *
 * Module owner: Admin (Builder A).
 */
class RulesPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final RuleTableModel model = new RuleTableModel();

    RulesPanel(RuleSet rules) {
        super(new BorderLayout());
        model.setRules(rules.getRules());

        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {45, 80, 80, 330, 80, 330};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(RuleTableModel.COL_SEVERITY).setCellRenderer(new Renderers.SeverityRenderer());

        JLabel header = new JLabel(rules.getRules().size() + " rules for the products table. Every change the triggers record "
                + "is checked against all of them.");
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel how = new JLabel("<html>One change can match several rules (e.g. price set to 0 matches R1 and R2): "
                + "it still becomes <b>ONE</b> alert, with the <b>highest</b> severity, and the message lists every rule "
                + "that matched. Threshold rules fire only when the value <i>crosses</i> the line. "
                + "The rules are defined in <b>src/dbmonitor/rules/ProductRules.java</b>.</html>");
        how.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        setBorder(BorderFactory.createTitledBorder("RULES - what turns a change into an alert"));
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(how, BorderLayout.SOUTH);
    }

    int count() {
        return model.getRowCount();
    }
}

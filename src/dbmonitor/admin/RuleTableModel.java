package dbmonitor.admin;

import dbmonitor.rules.Rule;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the Admin's "Rules" tab: the product rules R1-R10 (see ProductRules).
 * Every Rule subclass describes itself (describeCondition) - no type checks here.
 *
 * Module owner: Admin (Builder A).
 */
public class RuleTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final int COL_SEVERITY = 4;

    private static final String[] COLUMN_NAMES = {"Rule", "Type", "Table", "Fires when", "Severity", "Description"};

    private List<Rule> rules = new ArrayList<Rule>();

    public void setRules(List<Rule> newRules) {
        this.rules = new ArrayList<Rule>(newRules);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rules.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Rule r = rules.get(row);
        switch (column) {
            case 0:
                return r.getId();
            case 1:
                return r.getTypeKeyword();
            case 2:
                return r.getTable();
            case 3:
                return r.describeCondition();
            case COL_SEVERITY:
                return r.getSeverity();
            case 5:
                return r.getDescription();
            default:
                return "";
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}

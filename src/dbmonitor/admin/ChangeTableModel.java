package dbmonitor.admin;

import dbmonitor.rules.DataChange;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the Admin's "Changes" tab: every change the triggers recorded, and what the rules decided.
 * Used only on the Swing EDT.
 *
 * Module owner: Admin (Builder A).
 */
public class ChangeTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final String PENDING = "waiting for the server...";
    static final int COL_SUMMARY = 4;
    static final int COL_OUTCOME = 7;

    private static final String[] COLUMN_NAMES = {
            "Change #", "Table", "Row", "Type", "What changed", "Changed by", "When", "Outcome"
    };

    private List<DataChange> changes = new ArrayList<DataChange>();

    public void setChanges(List<DataChange> newChanges) {
        this.changes = new ArrayList<DataChange>(newChanges);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return changes.size();
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
        DataChange c = changes.get(row);
        switch (column) {
            case 0:
                return c.getChangeId();
            case 1:
                return c.getTableName();
            case 2:
                return c.getRowId();
            case 3:
                return c.getType() == null ? "?" : c.getType().name();
            case COL_SUMMARY:
                return c.summary();
            case 5:
                return c.getChangedBy();
            case 6:
                return c.getFormattedChangedAt();
            case COL_OUTCOME:
                return c.isProcessed() ? c.getOutcome() : PENDING;
            default:
                return "";
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}

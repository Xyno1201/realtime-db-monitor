package dbmonitor.admin;

import dbmonitor.common.AlertStatus;
import dbmonitor.db.BroadcastLogEntry;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the Admin's "Broadcast Log" tab (scope change SC-1): one row per push the server made.
 * Used only on the Swing EDT.
 *
 * Module owner: M1.
 */
public class BroadcastLogTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final int COL_STATUS = 6;

    private static final String[] COLUMN_NAMES = {
            "Log #", "Alert ID", "Type", "Severity", "Source", "Message", "Status pushed", "Dashboards", "Broadcast at"
    };

    private List<BroadcastLogEntry> entries = new ArrayList<BroadcastLogEntry>();

    public void setEntries(List<BroadcastLogEntry> newEntries) {
        this.entries = new ArrayList<BroadcastLogEntry>(newEntries);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return entries.size();
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
        BroadcastLogEntry e = entries.get(row);
        switch (column) {
            case 0:
                return e.getLogId();
            case 1:
                return e.getAlertId();
            case 2:
                return e.getType();
            case 3:
                return e.getSeverity();
            case 4:
                return e.getSource();
            case 5:
                return e.getMessage();
            case COL_STATUS:
                return toStatus(e.getBroadcastStatus());
            case 7:
                return e.getDashboardCount();
            case 8:
                return e.getFormattedBroadcastAt();
            default:
                return "";
        }
    }

    /** A known status is shown as a coloured flag; anything unexpected is shown as plain text. */
    private static Object toStatus(String value) {
        if (value == null) {
            return null;
        }
        for (AlertStatus s : AlertStatus.values()) {
            if (s.name().equals(value)) {
                return s;
            }
        }
        return value;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;   // the log is history: it is never edited from the GUI
    }
}

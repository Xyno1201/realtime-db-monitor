package dbmonitor.admin;

import dbmonitor.common.Alert;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Feeds the Admin JTable from a List of Alerts (the "Read" of CRUD, made visible).
 * Columns are the ones section 77 requires: id, type, severity, source, message, status,
 * pushed_status, created_at.
 *
 * Used only on the Swing EDT (setAlerts is called from SwingWorker.done()).
 *
 * Module owner: M1.
 */
public class AlertTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final int COL_ID = 0;
    static final int COL_TYPE = 1;
    static final int COL_SEVERITY = 2;
    static final int COL_SOURCE = 3;
    static final int COL_MESSAGE = 4;
    static final int COL_STATUS = 5;
    static final int COL_PUSHED = 6;
    static final int COL_CREATED = 7;

    public enum SortOrder {
        NONE("No Sorting"),
        HIGH_PRIORITY_FIRST("High priority first"),
        LOW_PRIORITY_FIRST("Low priority first");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String[] COLUMN_NAMES = {
            "ID", "Type", "Severity", "Source", "Message", "Status", "Pushed status", "Created"
    };

    private List<Alert> sourceAlerts = new ArrayList<Alert>();
    private List<Alert> alerts = new ArrayList<Alert>();
    private SortOrder sortOrder = SortOrder.NONE;

    /** Replaces all rows and redraws the table. */
    public void setAlerts(List<Alert> newAlerts) {
        this.sourceAlerts = new ArrayList<Alert>(newAlerts);
        sortAlerts();
        fireTableDataChanged();
    }

    /** Changes the display order without changing the alerts stored in the database. */
    public void setSortOrder(SortOrder sortOrder) {
        if (sortOrder == null) {
            throw new IllegalArgumentException("Sort order cannot be null");
        }
        this.sortOrder = sortOrder;
        sortAlerts();
        fireTableDataChanged();
    }

    public Alert getAlertAt(int row) {
        return alerts.get(row);
    }

    /** @return the row showing this alert id, or -1 if it is not in the table */
    public int rowOfId(int id) {
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return alerts.size();
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
        Alert a = alerts.get(row);
        switch (column) {
            case COL_ID:
                return a.getId();
            case COL_TYPE:
                return a.getTypeCode();
            case COL_SEVERITY:
                return a.getSeverity();
            case COL_SOURCE:
                return a.getSource();
            case COL_MESSAGE:
                return a.getMessage();
            case COL_STATUS:
                return a.getStatus();
            case COL_PUSHED:
                return a.getPushedStatus();   // may be null = never broadcast
            case COL_CREATED:
                return a.getFormattedCreatedAt();
            default:
                return "";
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;   // changes go through the buttons (and the DAO), never by typing in the table
    }

    private void sortAlerts() {
        alerts = new ArrayList<Alert>(sourceAlerts);
        if (sortOrder == SortOrder.NONE) {
            return;
        }
        final int direction = sortOrder == SortOrder.HIGH_PRIORITY_FIRST ? -1 : 1;
        Collections.sort(alerts, new Comparator<Alert>() {
            @Override
            public int compare(Alert first, Alert second) {
                return direction * first.getSeverity().compareTo(second.getSeverity());
            }
        });
    }
}

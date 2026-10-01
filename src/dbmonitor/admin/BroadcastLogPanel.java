package dbmonitor.admin;

import dbmonitor.db.BroadcastLogEntry;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.util.List;

/**
 * "Broadcast Log" tab (read-only): every push the server made to the dashboards - which alert,
 * the status pushed, how many dashboards were connected, when. Survives Purge (audit trail).
 *
 * Module owner: Admin (Builder A).
 */
class BroadcastLogPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int ROWS_SHOWN = 200;

    private final BroadcastLogTableModel model = new BroadcastLogTableModel();

    BroadcastLogPanel() {
        super(new BorderLayout());
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(3).setCellRenderer(new Renderers.SeverityRenderer());
        table.getColumnModel().getColumn(BroadcastLogTableModel.COL_STATUS).setCellRenderer(new Renderers.FlagRenderer());
        int[] widths = {50, 55, 135, 80, 100, 380, 100, 75, 140};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JLabel note = new JLabel("<html>Every time the server pushes an alert to the dashboards it records it here: which alert, "
                + "the status it pushed, how many dashboards were connected, and when. This history is kept even after "
                + "<i>Purge Resolved</i> deletes the alert itself. Newest first, last " + ROWS_SHOWN + " shown.</html>");
        note.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        setBorder(BorderFactory.createTitledBorder("BROADCAST LOG - table alert_broadcast_log (written by the server)"));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(note, BorderLayout.SOUTH);
    }

    void setEntries(List<BroadcastLogEntry> entries) {
        model.setEntries(entries);
    }

    int count() {
        return model.getRowCount();
    }
}

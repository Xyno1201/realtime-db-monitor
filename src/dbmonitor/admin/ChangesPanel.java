package dbmonitor.admin;

import dbmonitor.rules.DataChange;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.util.List;

/**
 * "Changes" tab (read-only): every INSERT / UPDATE / DELETE the triggers recorded on a monitored
 * table - from Workbench, from the Employee app or from any other program - and what the rules
 * decided about it. Nothing is hidden: changes that matched no rule are listed too.
 *
 * Module owner: Admin (Builder A).
 */
class ChangesPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int ROWS_SHOWN = 200;

    private final ChangeTableModel model = new ChangeTableModel();

    ChangesPanel() {
        super(new BorderLayout());
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {65, 70, 45, 65, 380, 140, 140, 190};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(ChangeTableModel.COL_OUTCOME).setCellRenderer(new Renderers.OutcomeRenderer());

        JLabel note = new JLabel("<html>Recorded automatically by MySQL <b>triggers</b> on each monitored table. "
                + "<i>Changed by</i> shows the MySQL account: <b>root@localhost</b> = edited directly (e.g. Workbench), "
                + "<b>alertapp@localhost</b> = edited through the Employee app. The server evaluates each change against the rules "
                + "and records the <i>Outcome</i>. Newest first, last " + ROWS_SHOWN + " shown.</html>");
        note.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        setBorder(BorderFactory.createTitledBorder("CHANGES - table data_changes (written by the triggers)"));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(note, BorderLayout.SOUTH);
    }

    void setChanges(List<DataChange> changes) {
        model.setChanges(changes);
    }

    int count() {
        return model.getRowCount();
    }
}

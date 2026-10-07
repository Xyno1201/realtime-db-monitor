package dbmonitor.admin;

import dbmonitor.common.Alert;
import dbmonitor.db.AlertDAO;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.List;

/**
 * "Alerts" tab: alerts the server created from rule-breaking changes, with their status flags.
 * The operator's job is here: Resolve (UPDATE), Purge Resolved / Delete Selected (DELETE).
 *
 * Alerts are no longer typed in by hand (v8): they appear when a change to a monitored table
 * breaks a rule.
 *
 * Module owner: Admin (Builder A).
 */
class AlertsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final AdminFrame frame;
    private final transient AlertDAO dao;
    private final AlertTableModel model = new AlertTableModel();
    private final JTable table = new JTable(model);
    private final JComboBox<AlertTableModel.SortOrder> sortSelector =
            new JComboBox<AlertTableModel.SortOrder>(AlertTableModel.SortOrder.values());
    private int selectAfterRefresh = -1;   // EDT only

    AlertsPanel(AdminFrame frame, AlertDAO dao) {
        super(new BorderLayout());
        this.frame = frame;
        this.dao = dao;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {40, 130, 75, 110, 430, 85, 95, 140};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(AlertTableModel.COL_SEVERITY).setCellRenderer(new Renderers.SeverityRenderer());
        table.getColumnModel().getColumn(AlertTableModel.COL_STATUS).setCellRenderer(new Renderers.FlagRenderer());
        table.getColumnModel().getColumn(AlertTableModel.COL_PUSHED).setCellRenderer(new Renderers.FlagRenderer());

        JLabel legend = new JLabel("<html>Alerts are created <b>automatically</b> when a change to a monitored table breaks a rule "
                + "(see the Changes and Rules tabs). &nbsp;Flags: <b><font color='#1565C0'>NEW</font></b> created, not yet broadcast"
                + " &rarr; <b><font color='#EF6C00'>SENT</font></b> pushed to the dashboards"
                + " &rarr; <b><font color='#2E7D32'>RESOLVED</font></b> closed by the operator."
                + " &nbsp;<i>Pushed status</i> = what the server last broadcast.</html>");
        legend.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createTitledBorder("ALERTS - newest first"));
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(legend, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(button("Refresh", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AlertsPanel.this.frame.refreshAll(true);
            }
        }));
        buttons.add(new JLabel("Sort alerts:"));
        sortSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applySortOrder((AlertTableModel.SortOrder) sortSelector.getSelectedItem());
            }
        });
        buttons.add(sortSelector);
        buttons.add(button("UPDATE: Resolve", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onResolve();
            }
        }));
        buttons.add(button("DELETE: Purge Resolved", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onPurge();
            }
        }));
        buttons.add(button("DELETE: Delete Selected", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDelete();
            }
        }));

        add(center, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    /** New data from AdminFrame's refresh (EDT). Keeps the selected alert selected. */
    void setAlerts(List<Alert> alerts) {
        Alert selected = selected(null);
        model.setAlerts(alerts);
        int id = selectAfterRefresh > 0 ? selectAfterRefresh : (selected == null ? -1 : selected.getId());
        selectAfterRefresh = -1;
        int row = id > 0 ? model.rowOfId(id) : -1;
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
        }
    }

    private void applySortOrder(AlertTableModel.SortOrder sortOrder) {
        Alert selected = selected(null);
        model.setSortOrder(sortOrder);
        if (selected != null) {
            int row = model.rowOfId(selected.getId());
            if (row >= 0) {
                table.setRowSelectionInterval(row, row);
            }
        }
    }

    int count() {
        return model.getRowCount();
    }

    private void onResolve() {
        final Alert a = selected("resolve");
        if (a == null) {
            return;
        }
        new DbTask<Integer>(frame, "Resolve", true) {
            @Override
            protected Integer work() throws SQLException {
                return dao.resolve(a.getId());
            }

            @Override
            protected void succeeded(Integer changed) {
                if (changed == 0) {
                    JOptionPane.showMessageDialog(frame, "Alert #" + a.getId() + " is already RESOLVED (or was deleted).",
                            "Nothing to resolve", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    frame.showStatus("Resolved alert #" + a.getId() + " - the server will broadcast RESOLVED on its next poll.", false);
                }
                selectAfterRefresh = a.getId();
                frame.refreshAll(false);
            }
        }.execute();
    }

    private void onPurge() {
        new DbTask<Integer>(frame, "Purge Resolved", true) {
            @Override
            protected Integer work() throws SQLException {
                return dao.purgeResolved();
            }

            @Override
            protected void succeeded(Integer deleted) {
                frame.showStatus(deleted == 0
                        ? "Nothing purged. Only RESOLVED alerts whose RESOLVED state has already been broadcast can be purged."
                        : "Purged " + deleted + " resolved alert(s). Their history stays in the Broadcast Log.", false);
                frame.refreshAll(false);
            }
        }.execute();
    }

    private void onDelete() {
        final Alert a = selected("delete");
        if (a == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(frame, "Delete alert #" + a.getId() + " permanently?\n(Deletes are not broadcast to dashboards.)",
                "Delete alert", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        new DbTask<Integer>(frame, "Delete Selected", true) {
            @Override
            protected Integer work() throws SQLException {
                return dao.deleteById(a.getId());
            }

            @Override
            protected void succeeded(Integer deleted) {
                frame.showStatus(deleted == 0 ? "Alert #" + a.getId() + " was already gone." : "Deleted alert #" + a.getId() + ".", false);
                frame.refreshAll(false);
            }
        }.execute();
    }

    /** @param action verb for the "select a row first" message, or null to stay silent */
    private Alert selected(String action) {
        int row = table.getSelectedRow();
        if (row < 0) {
            if (action != null) {
                JOptionPane.showMessageDialog(frame, "Select an alert in the table first, then " + action + " it.",
                        "No alert selected", JOptionPane.INFORMATION_MESSAGE);
            }
            return null;
        }
        return model.getAlertAt(row);
    }

    private static JButton button(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }
}

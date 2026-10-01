package dbmonitor.admin;

import dbmonitor.common.AlertStatus;
import dbmonitor.common.Severity;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Cell renderers shared by the Admin tables: coloured status "flags", coloured severities and
 * coloured change outcomes. Renderers only run on the Swing EDT.
 *
 * Module owner: Admin (Builder A).
 */
final class Renderers {

    static final Color FLAG_NEW = new Color(0x1565C0);       // blue
    static final Color FLAG_SENT = new Color(0xEF6C00);      // orange
    static final Color FLAG_RESOLVED = new Color(0x2E7D32);  // green
    static final Color GREY = new Color(0x9E9E9E);

    private Renderers() {
    }

    /** NEW / SENT / RESOLVED as a coloured flag; null as a grey "none"; other text as plain text. */
    static final class FlagRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setOpaque(true);
            label.setBorder(null);
            if (!(value instanceof AlertStatus)) {
                label.setText(value == null ? "none" : String.valueOf(value));
                label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                label.setForeground(value == null ? GREY : table.getForeground());
                return label;
            }
            AlertStatus status = (AlertStatus) value;
            label.setText(status.name());
            label.setForeground(Color.WHITE);
            label.setBackground(status == AlertStatus.NEW ? FLAG_NEW
                    : status == AlertStatus.SENT ? FLAG_SENT : FLAG_RESOLVED);
            if (isSelected) {
                label.setBorder(BorderFactory.createLineBorder(table.getSelectionBackground(), 3));
            }
            return label;
        }
    }

    /** A Severity (or severity text) in its own colour. */
    static final class SeverityRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Severity s = toSeverity(value);
            if (s != null) {
                label.setText(s.name());
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    label.setForeground(s.getColor());
                }
            }
            return label;
        }

        private static Severity toSeverity(Object value) {
            if (value instanceof Severity) {
                return (Severity) value;
            }
            if (value != null) {
                for (Severity s : Severity.values()) {
                    if (s.name().equals(String.valueOf(value))) {
                        return s;
                    }
                }
            }
            return null;
        }
    }

    /** Changes tab outcome: "ALERT #n (SEV)" in the severity colour, "waiting..." in orange, others grey. */
    static final class OutcomeRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : String.valueOf(value);
            label.setFont(label.getFont().deriveFont(text.startsWith("ALERT") ? Font.BOLD : Font.PLAIN));
            if (isSelected) {
                return label;
            }
            if (text.startsWith("ALERT")) {
                Color c = table.getForeground();
                for (Severity s : Severity.values()) {
                    if (text.contains("(" + s.name() + ")")) {
                        c = s.getColor();
                    }
                }
                label.setForeground(c);
            } else if (text.startsWith(ChangeTableModel.PENDING)) {
                label.setForeground(FLAG_SENT);
            } else {
                label.setForeground(GREY);
            }
            return label;
        }
    }
}

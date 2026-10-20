package dbmonitor.client;

import dbmonitor.common.Alert;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;

/**
 * One alert on the dashboard (master context v7, sections 37 and 76).
 *
 * <pre>
 *   +--+------------------------------------------------------------+
 *   |  | [CHG] Record Changed   CRITICAL   #12               NEW     |
 *   |  | Change detected in products #3: [R1] price set to 0 ...    |
 *   |  | products #3  -  2026-10-01 14:03:22                        |
 *   +--+------------------------------------------------------------+
 *    ^ accent stripe in the alert type's colour
 * </pre>
 *
 * POLYMORPHISM: the card never asks which subclass it holds (no instanceof, no switch on the
 * type). It calls getIconText(), getTypeLabel(), getColor() and getDisplayMessage(), and each
 * Alert subclass answers for itself. A new alert type would appear correctly with no change here.
 *
 * A card is keyed by alert id in DashboardFrame. When the same id arrives again (a duplicate
 * delivery or a status change such as RESOLVED), update() repaints this SAME card - no new card.
 * A RESOLVED card turns grey and stays visible, so the change can be seen.
 *
 * EDT only: created and updated by DashboardFrame on the Swing thread.
 *
 * Module owner: Dashboard (Builder B).
 */
public class AlertCard extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color RESOLVED_BACKGROUND = new Color(0xEEEEEE);
    private static final Color RESOLVED_TEXT = new Color(0x9E9E9E);
    private static final Color NORMAL_TEXT = new Color(0x212121);
    private static final Color META_TEXT = new Color(0x616161);
    private static final Color BORDER = new Color(0xBDBDBD);

    private final JPanel stripe = new JPanel();
    private final JPanel content = new JPanel(new BorderLayout(0, 4));
    private final JPanel header = new JPanel();
    private final JLabel typeLabel = new JLabel();
    private final JLabel severityLabel = new JLabel();
    private final JLabel idLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final JTextArea messageArea = new JTextArea();
    private final JLabel metaLabel = new JLabel();

    private Alert alert;

    public AlertCard(Alert alert) {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 6, 0),           // gap to the next card
                BorderFactory.createLineBorder(BORDER)));

        stripe.setPreferredSize(new Dimension(8, 10));
        add(stripe, BorderLayout.WEST);

        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(false);
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 13f));
        severityLabel.setFont(severityLabel.getFont().deriveFont(Font.BOLD, 12f));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(typeLabel);
        header.add(Box.createHorizontalStrut(12));
        header.add(severityLabel);
        header.add(Box.createHorizontalStrut(12));
        header.add(idLabel);
        header.add(Box.createHorizontalGlue());
        header.add(statusLabel);

        // A wrapping, read-only text area: messages can be up to 255 characters and list several
        // rules ("[R2] ...; [R6] ..."), so the text wraps instead of being cut off.
        messageArea.setEditable(false);
        messageArea.setFocusable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setBorder(null);
        messageArea.setFont(typeLabel.getFont().deriveFont(Font.PLAIN, 13f));
        // By default setText() moves the caret and scrolls it into view, which made the dashboard
        // jump away from the newest card during a burst. This caret never moves, so it never scrolls.
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        messageArea.setCaret(caret);
        // A starting width, so the first layout already wraps; the real width is set by the layout.
        messageArea.setSize(new Dimension(400, Short.MAX_VALUE));

        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 11f));

        content.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        content.setOpaque(false);
        content.add(header, BorderLayout.NORTH);
        content.add(messageArea, BorderLayout.CENTER);
        content.add(metaLabel, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        update(alert);
    }

    /**
     * Shows the newest state of this alert. Alert objects are immutable, so the card simply keeps
     * the latest object received for its id.
     */
    public final void update(Alert newAlert) {
        this.alert = newAlert;
        boolean resolved = newAlert.isResolved();

        typeLabel.setText(newAlert.getIconText() + "  " + newAlert.getTypeLabel());
        severityLabel.setText(newAlert.getSeverity().getLabel().toUpperCase(Locale.ROOT));
        idLabel.setText("#" + newAlert.getId());
        statusLabel.setText(newAlert.getStatus().name());
        messageArea.setText(newAlert.getDisplayMessage());
        metaLabel.setText(newAlert.getSource() + "   -   " + newAlert.getFormattedCreatedAt());
        setToolTipText(newAlert.getTypeLabel() + " #" + newAlert.getId() + " - " + newAlert.getStatus());

        if (resolved) {
            // Grey everything: the alert is finished but stays on screen (section 37).
            setBackground(RESOLVED_BACKGROUND);
            stripe.setBackground(RESOLVED_TEXT);
            typeLabel.setForeground(RESOLVED_TEXT);
            severityLabel.setForeground(RESOLVED_TEXT);
            idLabel.setForeground(RESOLVED_TEXT);
            statusLabel.setForeground(RESOLVED_TEXT);
            messageArea.setForeground(RESOLVED_TEXT);
            metaLabel.setForeground(RESOLVED_TEXT);
        } else {
            setBackground(BACKGROUND);
            stripe.setBackground(newAlert.getColor());
            typeLabel.setForeground(newAlert.getColor());
            severityLabel.setForeground(newAlert.getSeverity().getColor());
            idLabel.setForeground(META_TEXT);
            statusLabel.setForeground(NORMAL_TEXT);
            messageArea.setForeground(NORMAL_TEXT);
            metaLabel.setForeground(META_TEXT);
        }
        revalidate();
        repaint();
    }

    /** The latest Alert shown on this card (DashboardFrame recomputes the counters from it). */
    public Alert getAlert() {
        return alert;
    }
}

package dbmonitor.client;

import dbmonitor.common.Alert;
import dbmonitor.common.Severity;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The dashboard window (master context v7, sections 28, 37-39, 49 and 76).
 *
 * <pre>
 *   +------------------------------------------------------------+
 *   | Live Alerts                    Connected to localhost:5050 |   status
 *   | Critical 1  High 0  Medium 2  Low 3   Resolved 1  Total 7  |   counters
 *   +------------------------------------------------------------+
 *   | [newest card]                                              |
 *   | [older card]                                               |   scrollable
 *   | ...                                                        |
 *   +------------------------------------------------------------+
 * </pre>
 *
 * EDT ONLY: every method here runs on the Swing thread. AlertListenerThread reaches this class
 * only through SwingUtilities.invokeLater, so no locking is needed.
 *
 * DE-DUPLICATION (section 28): cards are kept in a HashMap keyed by alert id.
 *   new id   -> new card at the top
 *   known id -> the SAME card is updated (duplicate delivery or a status change such as RESOLVED)
 * Delivery is at-least-once (section 27), so this is what keeps duplicates off the screen.
 *
 * COUNTERS (section 39): recomputed from ALL cards after every update, never incremented, so a
 * repeated delivery can never make them drift. The severity counters count OPEN alerts (not
 * resolved); "Resolved" and "Total" are shown separately.
 *
 * Cards stay on screen while the server is down; only the status line changes.
 *
 * Module owner: Dashboard (Builder B).
 */
public class DashboardFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color CONNECTED = new Color(0x2E7D32);     // green
    private static final Color RECONNECTING = new Color(0xEF6C00);  // orange
    private static final Color CONNECTING = new Color(0x616161);    // grey
    private static final Color RESOLVED_COUNTER = new Color(0x9E9E9E);

    /** Severities shown from most to least severe. */
    private static final Severity[] COUNTER_ORDER = {
            Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW
    };

    private final String host;
    private final int port;

    // HashMap: O(1) lookup of the card for an alert id. Only the EDT uses it, so no synchronization.
    private final Map<Integer, AlertCard> cards = new HashMap<Integer, AlertCard>();

    // EnumMap: compact, ordered map for keys that are enum constants (one label per severity).
    private final Map<Severity, JLabel> severityCounters = new EnumMap<Severity, JLabel>(Severity.class);

    private final JLabel statusLabel = new JLabel();
    private final JLabel resolvedCounter = new JLabel();
    private final JLabel totalCounter = new JLabel();
    private final JPanel cardList = new JPanel();
    private final JLabel emptyHint = new JLabel("No alerts yet. New alerts appear here automatically.",
            SwingConstants.CENTER);

    public DashboardFrame(String host, int port) {
        super("Dashboard - Real-Time Database Monitor");
        this.host = host;
        this.port = port;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        add(buildTop(), BorderLayout.NORTH);
        add(buildCardArea(), BorderLayout.CENTER);

        setSize(620, 720);
        setMinimumSize(new Dimension(420, 300));
        setLocationByPlatform(true);   // several dashboards do not open exactly on top of each other

        showConnecting();
        recomputeCounters();
    }

    // =====================================================================
    // Called by AlertListenerThread through invokeLater (EDT)
    // =====================================================================

    /** Adds a card for a new alert id, or updates the existing card for a known id. */
    public void showAlert(Alert alert) {
        AlertCard card = cards.get(alert.getId());
        if (card == null) {
            card = new AlertCard(alert);
            cards.put(alert.getId(), card);
            cardList.add(card, 0);          // newest at the top
            emptyHint.setVisible(false);
        } else {
            card.update(alert);             // same id: same card, no duplicate (section 28)
        }
        cardList.revalidate();
        cardList.repaint();
        recomputeCounters();
    }

    public void showConnecting() {
        setStatus("Connecting to " + host + ":" + port + " ...", CONNECTING);
    }

    public void showConnected() {
        setStatus("Connected to " + host + ":" + port, CONNECTED);
    }

    public void showReconnecting(String reason, int reconnectMs) {
        setStatus("Reconnecting every " + (reconnectMs / 1000.0) + " s: " + reason, RECONNECTING);
    }

    // =====================================================================
    // Counters
    // =====================================================================

    /** Recounts from the current state of every card (section 39). */
    private void recomputeCounters() {
        Map<Severity, Integer> open = new EnumMap<Severity, Integer>(Severity.class);
        for (Severity s : Severity.values()) {
            open.put(s, 0);
        }
        int resolved = 0;
        for (AlertCard card : cards.values()) {
            Alert a = card.getAlert();
            if (a.isResolved()) {
                resolved++;
            } else {
                open.put(a.getSeverity(), open.get(a.getSeverity()) + 1);
            }
        }
        for (Severity s : COUNTER_ORDER) {
            severityCounters.get(s).setText(s.getLabel() + ": " + open.get(s));
        }
        resolvedCounter.setText("Resolved: " + resolved);
        totalCounter.setText("Total: " + cards.size());
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private JPanel buildTop() {
        JLabel title = new JLabel("Live Alerts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(statusLabel, BorderLayout.CENTER);

        JPanel counterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 4));
        for (Severity s : COUNTER_ORDER) {
            JLabel label = new JLabel();
            label.setForeground(s.getColor());
            label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
            severityCounters.put(s, label);
            counterRow.add(label);
        }
        resolvedCounter.setForeground(RESOLVED_COUNTER);
        resolvedCounter.setFont(resolvedCounter.getFont().deriveFont(Font.BOLD, 13f));
        totalCounter.setFont(totalCounter.getFont().deriveFont(Font.BOLD, 13f));
        counterRow.add(resolvedCounter);
        counterRow.add(totalCounter);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xBDBDBD)),
                BorderFactory.createEmptyBorder(8, 10, 6, 10)));
        top.add(titleRow, BorderLayout.NORTH);
        top.add(counterRow, BorderLayout.SOUTH);
        return top;
    }

    private JScrollPane buildCardArea() {
        cardList.setLayout(new BoxLayout(cardList, BoxLayout.Y_AXIS));
        cardList.setOpaque(false);

        emptyHint.setForeground(CONNECTING);
        emptyHint.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));

        // The cards sit at the TOP of a panel that follows the viewport's width: cards are never
        // stretched vertically, and long messages wrap to the window width instead of scrolling.
        WidthTrackingPanel holder = new WidthTrackingPanel();
        holder.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        holder.add(cardList, BorderLayout.NORTH);
        holder.add(emptyHint, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(holder,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        return scroll;
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
        statusLabel.setToolTipText(text);
    }

    /** A panel whose width always equals the scroll pane's visible width (no horizontal scroll). */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = 1L;

        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 32, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}

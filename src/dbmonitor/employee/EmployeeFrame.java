package dbmonitor.employee;

import dbmonitor.common.AppConfig;
import dbmonitor.db.InvalidProductException;
import dbmonitor.db.Product;
import dbmonitor.db.ProductDAO;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The EMPLOYEE APP: a separate window where staff manage the product catalogue.
 *
 *   CREATE  Add as New       READ    the table (search + sort, auto-refresh)
 *   UPDATE  Save Changes     DELETE  Delete
 *
 * This is the "normal business app" of the story. It knows nothing about alerts or rules - it just
 * edits the products table through ProductDAO. The MySQL triggers record every edit it makes
 * (as alertapp@localhost), exactly like an edit made directly in Workbench (root@localhost), and
 * the server decides whether that edit deserves an alert. The Admin console shows the result.
 *
 * The app validates its input (no negative price, etc.); Workbench does not.
 *
 * THREADING: every database call runs in an EmployeeTask (SwingWorker), never on the Swing thread.
 *
 * Module owner: Employee app (Builder A).
 */
public class EmployeeFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color STATUS_OK = new Color(0x2E7D32);
    private static final Color STATUS_ERROR = new Color(0xC62828);

    private final transient ProductDAO dao;

    private final ProductTableModel model = new ProductTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<ProductTableModel> sorter = new TableRowSorter<ProductTableModel>(model);
    private final JTextField searchField = new JTextField(22);
    private final JLabel countLabel = new JLabel(" ");

    private final JLabel idLabel = new JLabel("(new)");
    private final JTextField nameField = new JTextField(22);
    private final JTextField categoryField = new JTextField(12);
    private final JTextField priceField = new JTextField(9);
    private final JTextField stockField = new JTextField(6);

    private final JLabel statusLine = new JLabel(" ");
    private final Timer refreshTimer;

    // EDT-only state
    private int formProductId = 0;          // 0 = the form holds a new product
    private int selectAfterRefresh = -1;
    private boolean refreshing = false;     // ignore selection events caused by a refresh
    private boolean refreshInFlight = false;
    private long messageShownAt = 0;

    public EmployeeFrame(AppConfig config, ProductDAO dao) {
        super("Product Catalogue - Employee");
        this.dao = dao;

        add(buildTop(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 640);
        setLocationByPlatform(true);   // don't open exactly on top of the Admin window

        refreshTimer = new Timer(config.getAdminRefreshMs(), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh(false);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    /** Called once after the window is shown. */
    public void start() {
        refresh(false);
        refreshTimer.start();
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private JPanel buildTop() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JLabel title = new JLabel("Products");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        bar.add(title);
        bar.add(new JLabel("    Search:"));
        bar.add(searchField);
        bar.add(button("Refresh", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh(true);
            }
        }));
        bar.add(countLabel);

        searchField.setToolTipText("Filter by name or category");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applySearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applySearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applySearch();
            }
        });
        return bar;
    }

    private JScrollPane buildTable() {
        table.setRowSorter(sorter);   // click a column header to sort
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {50, 300, 150, 110, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && !refreshing) {
                    loadSelectedIntoForm();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 8, 0, 8), scroll.getBorder()));
        return scroll;
    }

    private JPanel buildBottom() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        String[] labels = {"ID", "Name", "Category", "Price (Rs)", "Stock"};
        java.awt.Component[] fields = {idLabel, nameField, categoryField, priceField, stockField};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = i;
            c.gridy = 0;
            form.add(new JLabel(labels[i]), c);
            c.gridy = 1;
            form.add(fields[i], c);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(button("Add as New", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onAdd();
            }
        }));
        buttons.add(button("Save Changes", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSave();
            }
        }));
        buttons.add(button("Delete", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDelete();
            }
        }));
        buttons.add(button("Clear Form", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearForm();
                table.clearSelection();
            }
        }));

        JPanel editor = new JPanel(new BorderLayout());
        editor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 8, 0, 8),
                BorderFactory.createTitledBorder("Product details - select a row to edit it, or fill in the form to add one")));
        editor.add(form, BorderLayout.NORTH);
        editor.add(buttons, BorderLayout.SOUTH);

        statusLine.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        statusLine.setFont(statusLine.getFont().deriveFont(Font.BOLD));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(editor, BorderLayout.CENTER);
        bottom.add(statusLine, BorderLayout.SOUTH);
        return bottom;
    }

    // =====================================================================
    // READ - load all products (timer + Refresh button + after every change)
    // =====================================================================

    void refresh(boolean userInitiated) {
        if (refreshInFlight) {
            return;   // previous refresh still running (e.g. MySQL slow) - don't pile up workers
        }
        refreshInFlight = true;
        new EmployeeTask<List<Product>>(this, "Refresh", userInitiated) {
            @Override
            protected List<Product> work() throws SQLException {
                return dao.readAll();
            }

            @Override
            protected void succeeded(List<Product> products) {
                setProducts(products);
                if (System.currentTimeMillis() - messageShownAt > 6000) {
                    setStatus("Connected - last updated " + new SimpleDateFormat("HH:mm:ss").format(new Date()), false);
                }
            }

            @Override
            protected void always() {
                refreshInFlight = false;
            }
        }.execute();
    }

    /** New data (EDT). Keeps the selected product selected and does NOT touch the form. */
    private void setProducts(List<Product> products) {
        int keepId = selectAfterRefresh > 0 ? selectAfterRefresh : selectedId();
        boolean loadAfter = selectAfterRefresh > 0;
        selectAfterRefresh = -1;
        refreshing = true;
        try {
            model.setProducts(products);
            int modelRow = keepId > 0 ? model.rowOfId(keepId) : -1;
            int viewRow = modelRow >= 0 ? table.convertRowIndexToView(modelRow) : -1;
            if (viewRow >= 0) {
                table.setRowSelectionInterval(viewRow, viewRow);
                if (loadAfter) {
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));   // e.g. a just-added product
                }
            }
        } finally {
            refreshing = false;
        }
        updateCount();
        if (loadAfter) {
            loadSelectedIntoForm();
        }
    }

    // =====================================================================
    // CREATE / UPDATE / DELETE
    // =====================================================================

    private void onAdd() {
        final String name = nameField.getText();
        final String category = categoryField.getText();
        final String price = priceField.getText();
        final String stock = stockField.getText();
        new EmployeeTask<Integer>(this, "Add product", true) {
            @Override
            protected Integer work() throws SQLException, InvalidProductException {
                return dao.create(name, category, price, stock);
            }

            @Override
            protected void succeeded(Integer id) {
                showStatus("Product #" + id + " added.", false);
                selectAfterRefresh = id;
                refresh(false);
            }
        }.execute();
    }

    private void onSave() {
        final int id = formProductId;
        if (id <= 0) {
            JOptionPane.showMessageDialog(this, "Select a product in the table first (or use Add as New).",
                    "No product selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final String name = nameField.getText();
        final String category = categoryField.getText();
        final String price = priceField.getText();
        final String stock = stockField.getText();
        new EmployeeTask<Integer>(this, "Save product", true) {
            @Override
            protected Integer work() throws SQLException, InvalidProductException {
                return dao.update(id, name, category, price, stock);
            }

            @Override
            protected void succeeded(Integer changed) {
                if (changed == 0) {
                    showStatus("Product #" + id + " no longer exists - someone else deleted it.", true);
                    clearForm();
                } else {
                    showStatus("Changes to product #" + id + " saved.", false);
                    selectAfterRefresh = id;
                }
                refresh(false);
            }
        }.execute();
    }

    private void onDelete() {
        final int id = formProductId;
        if (id <= 0) {
            JOptionPane.showMessageDialog(this, "Select a product in the table first.",
                    "No product selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Delete product #" + id + " (" + nameField.getText() + ")?",
                "Delete product", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        new EmployeeTask<Integer>(this, "Delete product", true) {
            @Override
            protected Integer work() throws SQLException {
                return dao.delete(id);
            }

            @Override
            protected void succeeded(Integer deleted) {
                showStatus(deleted == 0 ? "Product #" + id + " was already deleted." : "Product #" + id + " deleted.", false);
                clearForm();
                refresh(false);
            }
        }.execute();
    }

    // =====================================================================
    // Status line and failure reporting
    // =====================================================================

    /** Shows a message in the status line; it stays visible for 6 s before refresh info returns. */
    void showStatus(String text, boolean error) {
        setStatus(text, error);
        messageShownAt = System.currentTimeMillis();
    }

    private void setStatus(String text, boolean error) {
        statusLine.setText(text);
        statusLine.setForeground(error ? STATUS_ERROR : STATUS_OK);
    }

    /** Called by EmployeeTask on the EDT when a background database task failed. */
    void reportFailure(String action, Throwable cause, boolean dialog) {
        if (cause instanceof InvalidProductException) {
            // invalid form input - nothing was written
            showStatus(action + " rejected: " + cause.getMessage(), true);
            JOptionPane.showMessageDialog(this, cause.getMessage(), "Please check the product details",
                    JOptionPane.WARNING_MESSAGE);
        } else if (cause instanceof SQLException) {
            // MySQL unavailable - the window stays usable and recovers by itself
            System.err.println("[WARN] EmployeeFrame: " + action + " failed: " + cause.getMessage());
            showStatus("Database unavailable - " + action + " failed. Retrying automatically...", true);
            if (dialog) {
                JOptionPane.showMessageDialog(this, action + " failed because the database is unavailable:\n"
                                + cause.getMessage() + "\n\nNothing was saved. Try again once MySQL is running.",
                        "Database unavailable", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // a programming error, not an expected failure: report it loudly
            System.err.println("[ERROR] EmployeeFrame: unexpected error during " + action + ": " + cause);
            showStatus("Unexpected error during " + action + ": " + cause, true);
            if (dialog) {
                JOptionPane.showMessageDialog(this, "Unexpected error during " + action + ":\n" + cause,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =====================================================================
    // Helpers (EDT)
    // =====================================================================

    private void applySearch() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            // case-insensitive "contains", in the Name (1) and Category (2) columns
            sorter.setRowFilter(RowFilter.<ProductTableModel, Integer>regexFilter("(?i)" + Pattern.quote(text), 1, 2));
        }
        updateCount();
    }

    private void updateCount() {
        int shown = table.getRowCount();
        int total = model.getRowCount();
        countLabel.setText(shown == total ? total + " products" : shown + " of " + total + " products");
    }

    /** @return the product id of the selected row, or -1 */
    private int selectedId() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : model.getProductAt(table.convertRowIndexToModel(viewRow)).getId();
    }

    private void loadSelectedIntoForm() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        Product p = model.getProductAt(table.convertRowIndexToModel(viewRow));
        formProductId = p.getId();
        idLabel.setText("#" + p.getId());
        nameField.setText(p.getName());
        categoryField.setText(p.getCategory());
        priceField.setText(p.getPrice().toPlainString());
        stockField.setText(String.valueOf(p.getStock()));
    }

    private void clearForm() {
        formProductId = 0;
        idLabel.setText("(new)");
        nameField.setText("");
        categoryField.setText("");
        priceField.setText("");
        stockField.setText("");
    }

    private void shutdown() {
        refreshTimer.stop();   // stop timer -> close connection -> dispose
        dao.close();
        dispose();
    }

    private static JButton button(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }
}

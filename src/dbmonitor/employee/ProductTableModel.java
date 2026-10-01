package dbmonitor.employee;

import dbmonitor.db.Product;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the product table of the Employee app. Used only on the Swing EDT.
 *
 * Module owner: Employee app (Builder A).
 */
public class ProductTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMN_NAMES = {"ID", "Name", "Category", "Price", "Stock"};

    private List<Product> products = new ArrayList<Product>();

    public void setProducts(List<Product> newProducts) {
        this.products = new ArrayList<Product>(newProducts);
        fireTableDataChanged();
    }

    public Product getProductAt(int row) {
        return products.get(row);
    }

    /** @return the row showing this product id, or -1 */
    public int rowOfId(int id) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return products.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    /** Real column types, so clicking a header sorts numbers as numbers (not as text). */
    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
            case 4:
                return Integer.class;
            case 3:
                return BigDecimal.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        Product p = products.get(row);
        switch (column) {
            case 0:
                return p.getId();
            case 1:
                return p.getName();
            case 2:
                return p.getCategory();
            case 3:
                return p.getPrice();
            case 4:
                return p.getStock();
            default:
                return "";
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;   // edits go through the form + DAO, never by typing in the table
    }
}

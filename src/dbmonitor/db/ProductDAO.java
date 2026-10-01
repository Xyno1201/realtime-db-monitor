package dbmonitor.db;

import dbmonitor.common.AppConfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Full JDBC CRUD on the monitored products table - used by the Admin's Products tab
 * (the JDBC CRUD rubric line, done visibly in Java).
 *
 * Every change made here fires the same MySQL triggers as a change made in Workbench, so the
 * monitor sees it too; the Changes tab then shows changed_by = alertapp@localhost (the app)
 * instead of root@localhost (Workbench).
 *
 * All public methods are synchronized; this DAO owns its own connection.
 *
 * Module owner: database / Admin (Builder A).
 */
public class ProductDAO implements AutoCloseable {

    public static final int MAX_NAME = 100;
    public static final int MAX_CATEGORY = 50;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");   // DECIMAL(10,2)

    private static final String SQL_READ_ALL =
            "SELECT id, name, category, price, stock FROM products ORDER BY id";
    private static final String SQL_CREATE =
            "INSERT INTO products (name, category, price, stock) VALUES (?, ?, ?, ?)";
    private static final String SQL_UPDATE =
            "UPDATE products SET name = ?, category = ?, price = ?, stock = ? WHERE id = ?";
    private static final String SQL_DELETE =
            "DELETE FROM products WHERE id = ?";

    private final ManagedConnection db;

    public ProductDAO(AppConfig config) {
        this.db = new ManagedConnection(config, "ProductDAO");
    }

    /** READ: all products, by id. Never null. */
    public synchronized List<Product> readAll() throws SQLException {
        Connection conn = db.get();
        List<Product> list = new ArrayList<Product>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_READ_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Product(rs.getInt("id"), rs.getString("name"), rs.getString("category"),
                        rs.getBigDecimal("price"), rs.getInt("stock")));
            }
        }
        return list;
    }

    /** CREATE. @return the new product's id */
    public synchronized int create(String name, String category, String priceText, String stockText)
            throws InvalidProductException, SQLException {
        Product p = validate(0, name, category, priceText, stockText);
        Connection conn = db.get();
        try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE, Statement.RETURN_GENERATED_KEYS)) {
            fill(ps, p);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Product insert returned no generated id");
    }

    /** UPDATE. @return rows changed (0 if the product no longer exists) */
    public synchronized int update(int id, String name, String category, String priceText, String stockText)
            throws InvalidProductException, SQLException {
        Product p = validate(id, name, category, priceText, stockText);
        Connection conn = db.get();
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            fill(ps, p);
            ps.setInt(5, id);
            return ps.executeUpdate();
        }
    }

    /** DELETE. @return rows deleted (0 if already gone) */
    public synchronized int delete(int id) throws SQLException {
        Connection conn = db.get();
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    @Override
    public synchronized void close() {
        db.close();
    }

    // ---------------------------------------------------------------- validation

    /** Checks the form input and converts it. @throws InvalidProductException with a readable reason */
    static Product validate(int id, String name, String category, String priceText, String stockText)
            throws InvalidProductException {
        String n = name == null ? "" : name.trim();
        String c = category == null ? "" : category.trim();
        if (n.isEmpty()) {
            throw new InvalidProductException("Name must not be empty");
        }
        if (n.length() > MAX_NAME) {
            throw new InvalidProductException("Name is too long (maximum " + MAX_NAME + " characters)");
        }
        if (c.isEmpty()) {
            throw new InvalidProductException("Category must not be empty");
        }
        if (c.length() > MAX_CATEGORY) {
            throw new InvalidProductException("Category is too long (maximum " + MAX_CATEGORY + " characters)");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(priceText == null ? "" : priceText.trim());
        } catch (NumberFormatException e) {
            throw new InvalidProductException("Price must be a number, e.g. 499.00");
        }
        if (price.signum() < 0) {
            throw new InvalidProductException("Price cannot be negative");
        }
        if (price.compareTo(MAX_PRICE) > 0 || price.scale() > 2) {
            throw new InvalidProductException("Price must be at most 99999999.99 with 2 decimals");
        }
        int stock;
        try {
            stock = Integer.parseInt(stockText == null ? "" : stockText.trim());
        } catch (NumberFormatException e) {
            throw new InvalidProductException("Stock must be a whole number, e.g. 25");
        }
        if (stock < 0) {
            throw new InvalidProductException("Stock cannot be negative");
        }
        return new Product(id, n, c, price, stock);
    }

    private static void fill(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1, p.getName());
        ps.setString(2, p.getCategory());
        ps.setBigDecimal(3, p.getPrice());
        ps.setInt(4, p.getStock());
    }
}

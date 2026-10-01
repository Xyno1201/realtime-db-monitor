package dbmonitor.db;

import java.math.BigDecimal;

/**
 * One row of the monitored products table (the "company data").
 * Immutable - safe to pass from a SwingWorker thread to the Swing thread.
 *
 * Module owner: database / Admin (Builder A).
 */
public final class Product {

    private final int id;
    private final String name;
    private final String category;
    private final BigDecimal price;
    private final int stock;

    public Product(int id, String name, String category, BigDecimal price, int stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    @Override
    public String toString() {
        return "Product[#" + id + " " + name + ", " + category + ", " + price + ", stock " + stock + "]";
    }
}

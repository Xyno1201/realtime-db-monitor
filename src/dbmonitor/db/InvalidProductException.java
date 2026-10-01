package dbmonitor.db;

/**
 * Thrown when product data typed into the Admin's Products tab is invalid (empty name, negative
 * price, ...). Checked on purpose: the GUI must show the reason and insert nothing.
 *
 * Note for the viva: the APP validates, but someone editing the table directly in Workbench can
 * still type a negative price - and that is exactly what the monitor (rule R1) catches.
 *
 * Module owner: database / Admin (Builder A).
 */
public class InvalidProductException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidProductException(String message) {
        super(message);
    }
}

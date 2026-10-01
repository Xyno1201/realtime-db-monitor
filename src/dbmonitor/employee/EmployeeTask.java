package dbmonitor.employee;

import dbmonitor.db.InvalidProductException;

import javax.swing.SwingWorker;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * The SwingWorker pattern every Employee-app action uses (same idea as the Admin's DbTask):
 *
 *   work()       runs on a BACKGROUND thread - the database call. Never touch Swing here.
 *   succeeded()  runs on the Swing EDT afterwards - update the table and labels here.
 *   always()     runs on the EDT after success OR failure.
 *
 * Failures go to EmployeeFrame.reportFailure(): invalid input -> warning dialog; database
 * unavailable -> red status line (+ dialog for button clicks). A slow or stopped MySQL never
 * freezes the window.
 *
 * Module owner: Employee app (Builder A).
 */
abstract class EmployeeTask<T> extends SwingWorker<T, Void> {

    private final EmployeeFrame frame;
    private final String actionName;
    private final boolean dialogOnError;

    EmployeeTask(EmployeeFrame frame, String actionName, boolean dialogOnError) {
        this.frame = frame;
        this.actionName = actionName;
        this.dialogOnError = dialogOnError;
    }

    /** The database work. Runs on a background thread. */
    protected abstract T work() throws SQLException, InvalidProductException;

    /** Runs on the EDT with work()'s result. */
    protected abstract void succeeded(T result);

    /** Runs on the EDT after success or failure. */
    protected void always() {
    }

    @Override
    protected final T doInBackground() throws SQLException, InvalidProductException {
        return work();
    }

    @Override
    protected final void done() {
        try {
            succeeded(get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            frame.reportFailure(actionName, e.getCause(), dialogOnError);
        } finally {
            always();
        }
    }
}

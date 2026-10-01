package dbmonitor.admin;

import javax.swing.SwingWorker;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * The one SwingWorker pattern every Admin button uses (v7 section 24):
 *
 *   work()       runs on a BACKGROUND thread - the database call. Never touch Swing here.
 *   succeeded()  runs on the Swing EDT afterwards - update tables and labels here.
 *   always()     runs on the EDT after success OR failure.
 *
 * Failures are reported by AdminFrame.reportFailure(): database unavailable -> red status line (+ dialog for button clicks); anything else -> error.
 * So a slow or stopped MySQL never freezes the window.
 *
 * Module owner: Admin (Builder A).
 */
abstract class DbTask<T> extends SwingWorker<T, Void> {

    private final AdminFrame frame;
    private final String actionName;
    private final boolean dialogOnError;

    DbTask(AdminFrame frame, String actionName, boolean dialogOnError) {
        this.frame = frame;
        this.actionName = actionName;
        this.dialogOnError = dialogOnError;
    }

    /** The database work. Runs on a background thread. */
    protected abstract T work() throws SQLException;

    /** Runs on the EDT with work()'s result. */
    protected abstract void succeeded(T result);

    /** Runs on the EDT after success or failure. */
    protected void always() {
    }

    @Override
    protected final T doInBackground() throws SQLException {
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

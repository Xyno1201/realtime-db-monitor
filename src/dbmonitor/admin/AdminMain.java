package dbmonitor.admin;

import dbmonitor.common.AppConfig;
import dbmonitor.db.AlertDAO;
import dbmonitor.db.ChangeDAO;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.IOException;

/**
 * Entry point of the ADMIN process - the operator console (v8).
 *
 * The Admin works only with MySQL; it never talks to the server. Each DAO owns its own
 * connection (AlertDAO, ChangeDAO), and both are used from SwingWorker
 * threads, never from the Swing thread.
 *
 * Run from the project root:  java -cp "out;lib/*" dbmonitor.admin.AdminMain   (':' on Mac/Linux)
 *
 * Module owner: Admin (Builder A).
 */
public class AdminMain {

    public static void main(String[] args) {
        final AppConfig config;
        try {
            config = AppConfig.load(AppConfig.DEFAULT_PATH);
        } catch (IOException e) {
            String msg = "Cannot read " + AppConfig.DEFAULT_PATH + " (" + e.getMessage()
                    + ").\nStart the Admin from the project root folder.";
            System.err.println("[ERROR] AdminMain: " + msg);
            JOptionPane.showMessageDialog(null, msg, "Configuration missing", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException e) {
            System.err.println("[WARN] AdminMain: system look and feel unavailable, using default: " + e.getMessage());
        }

        final AlertDAO alertDao = new AlertDAO(config);
        final ChangeDAO changeDao = new ChangeDAO(config);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                AdminFrame frame = new AdminFrame(config, alertDao, changeDao);
                frame.setVisible(true);
                frame.start();
            }
        });
    }
}

package dbmonitor.employee;

import dbmonitor.common.AppConfig;
import dbmonitor.db.ProductDAO;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.IOException;

/**
 * Entry point of the EMPLOYEE APP - the product catalogue window staff use every day.
 *
 * A separate program from the Admin console: employees edit products here; the operator watches
 * alerts in the Admin. It works only with MySQL (one ProductDAO = one connection) and never
 * talks to the server.
 *
 * Run from the project root:  java -cp "out;lib/*" dbmonitor.employee.EmployeeMain   (':' on Mac/Linux)
 *                        or:  .\run.cmd employee
 *
 * Module owner: Employee app (Builder A).
 */
public class EmployeeMain {

    public static void main(String[] args) {
        final AppConfig config;
        try {
            config = AppConfig.load(AppConfig.DEFAULT_PATH);
        } catch (IOException e) {
            String msg = "Cannot read " + AppConfig.DEFAULT_PATH + " (" + e.getMessage()
                    + ").\nStart the Employee app from the project root folder.";
            System.err.println("[ERROR] EmployeeMain: " + msg);
            JOptionPane.showMessageDialog(null, msg, "Configuration missing", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException e) {
            System.err.println("[WARN] EmployeeMain: system look and feel unavailable, using default: " + e.getMessage());
        }

        final ProductDAO dao = new ProductDAO(config);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                EmployeeFrame frame = new EmployeeFrame(config, dao);
                frame.setVisible(true);
                frame.start();
            }
        });
    }
}

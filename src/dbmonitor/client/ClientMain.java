package dbmonitor.client;

import dbmonitor.common.AppConfig;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Entry point of a DASHBOARD process (master context v7, sections 6.3 and 76).
 *
 * The dashboard only talks to the server over TCP; it never connects to MySQL, so it needs only
 * server.host, server.port and client.reconnectMs from the configuration. If the file is missing,
 * the built-in defaults (localhost:5050, 3000 ms) are used, so a dashboard can still start.
 *
 *   main thread:  load config -> hand over to the EDT
 *   EDT:          build DashboardFrame -> start AlertListenerThread
 *   listener:     socket -> readObject() -> invokeLater -> DashboardFrame (see AlertListenerThread)
 *
 * Run from the project root:  java -cp "out;lib/*" dbmonitor.client.ClientMain   (':' on Mac/Linux)
 * Several dashboards can run at the same time (up to server.maxClients).
 *
 * Module owner: Dashboard (Builder B).
 */
public class ClientMain {

    public static void main(String[] args) {
        AppConfig loaded;
        try {
            loaded = AppConfig.load(AppConfig.DEFAULT_PATH);
        } catch (IOException e) {
            System.err.println("[WARN] ClientMain: cannot read " + AppConfig.DEFAULT_PATH + " ("
                    + e.getMessage() + ") - using built-in defaults");
            loaded = AppConfig.defaults();
        }
        final AppConfig config = loaded;
        System.out.println("[INFO] ClientMain: server " + config.getServerHost() + ":" + config.getServerPort()
                + ", reconnect every " + config.getClientReconnectMs() + " ms (" + config.getSource() + ")");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException e) {
            System.err.println("[WARN] ClientMain: system look and feel unavailable, using default: " + e.getMessage());
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final DashboardFrame frame = new DashboardFrame(config.getServerHost(), config.getServerPort());
                final AlertListenerThread listener = new AlertListenerThread(config.getServerHost(),
                        config.getServerPort(), config.getClientReconnectMs(), frame);

                // Closing the window stops the listener first (running = false, interrupt, close
                // socket); JFrame then exits because of EXIT_ON_CLOSE.
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        listener.shutdown();
                    }
                });

                frame.setVisible(true);
                listener.start();
            }
        });
    }
}

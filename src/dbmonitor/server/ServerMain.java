package dbmonitor.server;

import dbmonitor.common.AppConfig;
import dbmonitor.db.AlertDAO;
import dbmonitor.db.ChangeDAO;
import dbmonitor.rules.ProductRules;

import java.io.IOException;
import java.net.BindException;

/**
 * Entry point of the SERVER process (master context v7, section 6.1).
 *
 *   1. load config/app.properties
 *   2. create the server-owned AlertDAO (its JDBC connection opens on first use, so the server
 *      still starts when MySQL is down - the poller keeps retrying)
 *   3. start the BroadcastServer (port 5050) - exits with a clear error if the port is taken
 *   4. start the PollerThread (alerts -> dashboards)
 *   4b. start the ChangeDetectorThread (recorded data changes -> rules -> alerts)   [v8]
 *   5. register a shutdown hook (Ctrl+C / IDE stop button) for a clean stop (section 44)
 *
 * Run from the project root:  java -cp "out;lib/*" dbmonitor.server.ServerMain   (':' on Mac/Linux)
 *
 * Module owner: M2.
 */
public class ServerMain {

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.load(AppConfig.DEFAULT_PATH);
        } catch (IOException e) {
            System.err.println("[ERROR] ServerMain: cannot read " + AppConfig.DEFAULT_PATH + " (" + e.getMessage()
                    + "). Run from the project root folder.");
            System.exit(1);
            return;
        }
        System.out.println("[INFO] ServerMain: " + config.describe());

        final AlertDAO dao = new AlertDAO(config);
        final BroadcastServer server = new BroadcastServer(config.getServerPort(), config.getServerMaxClients());
        try {
            server.start();
        } catch (BindException e) {
            System.err.println("[ERROR] ServerMain: port " + config.getServerPort()
                    + " is already in use - is another ServerMain running? (" + e.getMessage() + ")");
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("[ERROR] ServerMain: could not start the TCP server: " + e.getMessage());
            System.exit(1);
            return;
        }

        final PollerThread poller = new PollerThread(dao, server, config.getPollIntervalMs());
        poller.start();

        // v8: turns changes recorded by the triggers into alerts, using the product rules R1-R10.
        // Its own ChangeDAO = its own connection (it uses transactions).
        final ChangeDAO changeDao = new ChangeDAO(config);
        final ChangeDetectorThread detector =
                new ChangeDetectorThread(changeDao, ProductRules.create(), config.getPollIntervalMs());
        detector.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("[INFO] ServerMain: shutting down...");
                detector.shutdown();
                poller.shutdown();
                try {
                    detector.join(3000);
                    poller.join(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                server.stop();
                changeDao.close();
                dao.close();
            }
        }, "ShutdownHook"));

        System.out.println("[INFO] ServerMain: running. Press Ctrl+C to stop.");
    }
}

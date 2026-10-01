import dbmonitor.common.Alert;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ConnectException;
import java.net.Socket;

/**
 * THROWAWAY HARNESS (master context v7, section 84.3) - a text-only stand-in for the real Swing
 * dashboard (Phases 8-9), so the TCP push can be seen before the GUI dashboard exists.
 *
 * Connects to localhost:5050, prints every Alert the server pushes, and reconnects every 3 s if
 * the server is not running or stops. Run several at once to see one alert reach all of them.
 *
 * Not part of the application (lives in test/). Stop it with Ctrl+C.
 */
public class ConsoleDashboard {

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;

        while (true) {
            try (Socket socket = new Socket(host, port);
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                System.out.println("[CONNECTED] to " + host + ":" + port + " - waiting for alerts...");
                while (true) {
                    Object received = in.readObject();
                    if (received instanceof Alert) {
                        Alert a = (Alert) received;
                        System.out.println(String.format("%-6s #%-4d %-9s %-8s | %s",
                                a.getIconText(), a.getId(), a.getSeverity(), a.getStatus(), a.getDisplayMessage()));
                    }
                }
            } catch (ConnectException e) {
                System.out.println("[RECONNECTING] server not running - retrying in 3 s");
            } catch (IOException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                System.out.println("[RECONNECTING] connection lost (" + reason + ") - retrying in 3 s");
            } catch (ClassNotFoundException e) {
                System.out.println("[ERROR] class mismatch - rebuild everything (" + e.getMessage() + ")");
            }
            Thread.sleep(3000);
        }
    }
}

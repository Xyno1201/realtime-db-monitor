package dbmonitor.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Immutable application configuration, read once from config/app.properties at program start.
 *
 * Keys and defaults are the contract in master context v7, sections 69.1 and 69.3.
 * Every value is read and validated ONCE in load(); the object is immutable afterwards, so it
 * can be shared safely between threads (poller, handlers, EDT) without synchronization.
 *
 * Error policy (each problem is logged once, at load time):
 *   - file missing / unreadable      -> IOException; the caller's main() decides (the Server and
 *                                       Admin cannot work without it; a Dashboard may use defaults()).
 *   - key missing or empty           -> [WARN] + contract default
 *   - number not parseable / invalid -> [WARN] + contract default
 *   - unknown key (e.g. a typo such as "poller.intervalMs") -> [WARN], ignored
 *
 * Usage (in each main()):
 *   AppConfig config = AppConfig.load(AppConfig.DEFAULT_PATH);
 *   int port = config.getServerPort();
 *
 * Module owner: M4 (common / configuration). Frozen once approved.
 */
public final class AppConfig {

    /** Relative to the working directory: run every program from the project root. */
    public static final String DEFAULT_PATH = "config/app.properties";

    // ---- Keys (section 69.3) ----
    public static final String KEY_DB_URL = "db.url";
    public static final String KEY_DB_USER = "db.user";
    public static final String KEY_DB_PASSWORD = "db.password";
    public static final String KEY_SERVER_HOST = "server.host";
    public static final String KEY_SERVER_PORT = "server.port";
    public static final String KEY_SERVER_MAX_CLIENTS = "server.maxClients";
    public static final String KEY_POLL_INTERVAL_MS = "poll.intervalMs";
    public static final String KEY_CLIENT_RECONNECT_MS = "client.reconnectMs";
    public static final String KEY_ADMIN_REFRESH_MS = "admin.refreshMs";

    // ---- Defaults (sections 69.1 and 69.3) ----
    public static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/alert_monitor?useSSL=false&allowPublicKeyRetrieval=true";
    public static final String DEFAULT_DB_USER = "alertapp";
    public static final String DEFAULT_DB_PASSWORD = "alertpass";
    public static final String DEFAULT_SERVER_HOST = "localhost";
    public static final int DEFAULT_SERVER_PORT = 5050;
    public static final int DEFAULT_SERVER_MAX_CLIENTS = 10;
    public static final int DEFAULT_POLL_INTERVAL_MS = 2000;
    public static final int DEFAULT_CLIENT_RECONNECT_MS = 3000;
    public static final int DEFAULT_ADMIN_REFRESH_MS = 3000;

    private static final Set<String> KNOWN_KEYS;

    static {
        Set<String> keys = new HashSet<String>();
        keys.add(KEY_DB_URL);
        keys.add(KEY_DB_USER);
        keys.add(KEY_DB_PASSWORD);
        keys.add(KEY_SERVER_HOST);
        keys.add(KEY_SERVER_PORT);
        keys.add(KEY_SERVER_MAX_CLIENTS);
        keys.add(KEY_POLL_INTERVAL_MS);
        keys.add(KEY_CLIENT_RECONNECT_MS);
        keys.add(KEY_ADMIN_REFRESH_MS);
        KNOWN_KEYS = Collections.unmodifiableSet(keys);
    }

    private final String source;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String serverHost;
    private final int serverPort;
    private final int serverMaxClients;
    private final int pollIntervalMs;
    private final int clientReconnectMs;
    private final int adminRefreshMs;

    private AppConfig(Properties props, String source) {
        this.source = source;
        warnAboutUnknownKeys(props);

        this.dbUrl = readString(props, KEY_DB_URL, DEFAULT_DB_URL, false);
        this.dbUser = readString(props, KEY_DB_USER, DEFAULT_DB_USER, false);
        this.dbPassword = readString(props, KEY_DB_PASSWORD, DEFAULT_DB_PASSWORD, true);
        this.serverHost = readString(props, KEY_SERVER_HOST, DEFAULT_SERVER_HOST, false);
        this.serverPort = readInt(props, KEY_SERVER_PORT, DEFAULT_SERVER_PORT, 1, 65535);
        this.serverMaxClients = readInt(props, KEY_SERVER_MAX_CLIENTS, DEFAULT_SERVER_MAX_CLIENTS, 1, 1000);
        this.pollIntervalMs = readInt(props, KEY_POLL_INTERVAL_MS, DEFAULT_POLL_INTERVAL_MS, 100, 600000);
        this.clientReconnectMs = readInt(props, KEY_CLIENT_RECONNECT_MS, DEFAULT_CLIENT_RECONNECT_MS, 100, 600000);
        this.adminRefreshMs = readInt(props, KEY_ADMIN_REFRESH_MS, DEFAULT_ADMIN_REFRESH_MS, 100, 600000);
    }

    // =====================================================================
    // Factories
    // =====================================================================

    /**
     * Reads and validates a properties file.
     *
     * @param path file path, normally {@link #DEFAULT_PATH}
     * @return the validated, immutable configuration
     * @throws IOException if the file does not exist or cannot be read
     */
    public static AppConfig load(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            throw new IOException("Configuration path is empty");
        }
        Properties props = new Properties();
        // try-with-resources: the file is closed even if load() throws
        try (InputStream in = new FileInputStream(path)) {
            props.load(in);
        }
        return new AppConfig(props, path);
    }

    /**
     * Configuration made only of the contract defaults (section 69.1/69.3), without reading a file.
     * Intended for a Dashboard that cannot find the file; it does not need the DB keys.
     */
    public static AppConfig defaults() {
        return new AppConfig(new Properties(), "built-in defaults");
    }

    // =====================================================================
    // Typed getters (values were validated once in the constructor)
    // =====================================================================

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getServerMaxClients() {
        return serverMaxClients;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public int getClientReconnectMs() {
        return clientReconnectMs;
    }

    public int getAdminRefreshMs() {
        return adminRefreshMs;
    }

    /** Where the values came from (file path or "built-in defaults"). */
    public String getSource() {
        return source;
    }

    /** Human-readable summary for startup logs. The password is never printed. */
    public String describe() {
        return "AppConfig[source=" + source
                + ", db.url=" + dbUrl
                + ", db.user=" + dbUser
                + ", db.password=" + (dbPassword.isEmpty() ? "(empty)" : "****")
                + ", server=" + serverHost + ":" + serverPort
                + ", server.maxClients=" + serverMaxClients
                + ", poll.intervalMs=" + pollIntervalMs
                + ", client.reconnectMs=" + clientReconnectMs
                + ", admin.refreshMs=" + adminRefreshMs + "]";
    }

    @Override
    public String toString() {
        return describe();
    }

    // =====================================================================
    // Parsing helpers
    // =====================================================================

    private String readString(Properties props, String key, String defaultValue, boolean emptyAllowed) {
        String raw = props.getProperty(key);
        if (raw == null) {
            if (!props.isEmpty()) {
                warn("missing key '" + key + "' - using default");
            }
            return defaultValue;
        }
        String value = raw.trim();
        if (value.isEmpty() && !emptyAllowed) {
            warn("key '" + key + "' is empty - using default");
            return defaultValue;
        }
        return value;
    }

    private int readInt(Properties props, String key, int defaultValue, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null) {
            if (!props.isEmpty()) {
                warn("missing key '" + key + "' - using default " + defaultValue);
            }
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            warn("key '" + key + "' has non-numeric value '" + raw + "' - using default " + defaultValue);
            return defaultValue;
        }
        if (value < min || value > max) {
            warn("key '" + key + "' = " + value + " is outside " + min + ".." + max
                    + " - using default " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    private void warnAboutUnknownKeys(Properties props) {
        for (String key : props.stringPropertyNames()) {
            if (!KNOWN_KEYS.contains(key)) {
                warn("unknown key '" + key + "' is ignored (typo? see master context section 69.3)");
            }
        }
    }

    private void warn(String message) {
        System.err.println("[WARN] AppConfig: " + message + " (" + source + ")");
    }
}

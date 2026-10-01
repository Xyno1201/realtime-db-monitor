package dbmonitor.db;

import dbmonitor.common.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * One long-lived JDBC connection that heals itself (v8 - replaces the connection code that used to
 * live inside AlertDAO).
 *
 * Every DAO object owns exactly ONE ManagedConnection and uses it only inside its own synchronized
 * methods. So a connection is never used by two threads at once, and a transaction on one DAO's
 * connection can never mix with another DAO's statements.
 *
 *   Server process: PollerThread -> AlertDAO (conn 1)    ChangeDetectorThread -> ChangeDAO (conn 2)
 *   Admin process:  AlertDAO (conn 1), ChangeDAO (conn 2), ProductDAO (conn 3)
 *
 * get() checks the connection with isValid(2) and reopens it if MySQL was restarted or dropped
 * an idle connection - that is how every part recovers from "MySQL was down" without a restart.
 *
 * Not thread-safe by itself: callers use it only while holding their own DAO's lock.
 *
 * Module owner: database (Builder A).
 */
public final class ManagedConnection implements AutoCloseable {

    private final AppConfig config;
    private final String owner;          // for log lines, e.g. "ChangeDAO"
    private Connection connection;

    public ManagedConnection(AppConfig config, String owner) {
        this.config = config;
        this.owner = owner;
    }

    /** Returns a live connection, opening or reopening it when needed. */
    public Connection get() throws SQLException {
        if (connection != null) {
            boolean alive;
            try {
                alive = connection.isValid(2);
            } catch (SQLException e) {
                alive = false;
            }
            if (alive) {
                return connection;
            }
            System.err.println("[WARN] " + owner + ": database connection lost - reconnecting");
            closeQuietly();
        }
        connection = DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
        System.out.println("[INFO] " + owner + ": connected to " + config.getDbUrl());
        return connection;
    }

    /** Closes the connection. Safe to call more than once. */
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[INFO] " + owner + ": database connection closed");
            } catch (SQLException e) {
                System.err.println("[WARN] " + owner + ": error while closing connection: " + e.getMessage());
            }
            connection = null;
        }
    }

    private void closeQuietly() {
        try {
            connection.close();
        } catch (SQLException e) {
            // the connection is already broken; there is nothing left to release
            System.err.println("[WARN] " + owner + ": could not close broken connection: " + e.getMessage());
        }
        connection = null;
    }
}

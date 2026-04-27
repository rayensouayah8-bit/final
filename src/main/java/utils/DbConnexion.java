package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Connexion JDBC simple (singleton), meme idee que {@code MyDatabase} du workshop :
 * {@link DriverManager}, constructeur prive, {@link #getInstance()}, {@link #getConnection()}.
 */
public final class DbConnexion {

    private static final String DB_NAME = "smart_voyage";
    private static final String URL_PARAMS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";
    private static final String BASE_URL = "jdbc:mysql://127.0.0.1:3306/" + URL_PARAMS;
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + DB_NAME + URL_PARAMS;
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private static final int MYSQL_ERR_UNKNOWN_DATABASE = 1049;

    private Connection connection;
    private static volatile DbConnexion instance;

    private DbConnexion() {
        try {
            connection = connectEnsuringDatabase();
            DbBootstrapper.ensureSchema(connection);
            System.out.println("Connection established");
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static Connection connectEnsuringDatabase() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            if (e.getErrorCode() != MYSQL_ERR_UNKNOWN_DATABASE) {
                throw e;
            }

            try (Connection server = DriverManager.getConnection(BASE_URL, USER, PASSWORD);
                 java.sql.Statement st = server.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            return DriverManager.getConnection(URL, USER, PASSWORD);
        }
    }

    public static DbConnexion getInstance() {
        if (instance == null) {
            synchronized (DbConnexion.class) {
                if (instance == null) {
                    instance = new DbConnexion();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = connectEnsuringDatabase();
            DbBootstrapper.ensureSchema(connection);
        }
        return connection;
    }

    public static void shutdown() {
        synchronized (DbConnexion.class) {
            if (instance != null && instance.connection != null) {
                try {
                    instance.connection.close();
                } catch (SQLException ignored) {
                }
                instance.connection = null;
                instance = null;
            }
        }
    }
}

package config;

public class DatabaseConfig {
    public static final String DB_URL = "jdbc:mysql://localhost:3306/skillswap";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "";
    public static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static final String RMI_HOST = "localhost";
    public static final int RMI_PORT = 1099;
    public static final String RMI_SERVICE_NAME = "SkillSwapService";

    public static final int CHAT_SERVER_PORT = 5500;
}

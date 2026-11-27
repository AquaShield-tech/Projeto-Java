package school.sptech;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class DBConnectionProvider {
    private final DataSource dataSource;

    public DBConnectionProvider() {
        String host = envOrDefault("DB_HOST", "localhost");
        String port = envOrDefault("DB_PORT", "3306");
        String dbName = envOrDefault("DB_NAME", "aquashield");
        String user = envOrDefault("DB_USER", "root");
        String password = envOrDefault("DB_PASSWORD", "@K8m1m4v3a12");

        BasicDataSource basicDataSource = new BasicDataSource();
        basicDataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        basicDataSource.setUsername(user);
        basicDataSource.setPassword(password);

        this.dataSource = basicDataSource;
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    public JdbcTemplate getConnection() {
        return new JdbcTemplate(dataSource);
    }
}
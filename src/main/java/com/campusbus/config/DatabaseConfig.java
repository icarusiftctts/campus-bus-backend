package com.campusbus.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.campusbus.repository")
@EnableTransactionManagement
public class DatabaseConfig {

    @Bean
    // amazonq-ignore-next-line
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // Use RDS Proxy endpoint
        String proxyEndpoint = System.getenv("RDS_PROXY_ENDPOINT");
        if (proxyEndpoint == null) {
            throw new RuntimeException("RDS_PROXY_ENDPOINT environment variable not set");
        }
        String dbUrl = "jdbc:mysql://" + proxyEndpoint + ":3306/campusbus?useSSL=true&requireSSL=true&serverTimezone=UTC";

        config.setJdbcUrl(dbUrl);
        config.setUsername(System.getenv("DB_USERNAME"));
        if (System.getenv("DB_USERNAME") == null) {
            throw new RuntimeException("Database Username environment variable not set");
        }

        config.setPassword(System.getenv("DB_PASSWORD"));
        if (System.getenv("DB_PASSWORD") == null) {
            throw new RuntimeException("Database Password environment variable not set");
        }

        config.setMaximumPoolSize(1);                      // Critical for Lambda
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(60000);
        config.setLeakDetectionThreshold(60000);

        return new HikariDataSource(config);
    }


}
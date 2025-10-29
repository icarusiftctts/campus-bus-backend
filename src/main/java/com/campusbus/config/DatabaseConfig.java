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
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        String proxyEndpoint = System.getenv("RDS_PROXY_ENDPOINT");
        if (proxyEndpoint == null) {
            throw new RuntimeException("RDS_PROXY_ENDPOINT environment variable not set");
        }
        
        config.setJdbcUrl("jdbc:mysql://" + proxyEndpoint + ":3306/campusbus?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&cachePrepStmts=true&useServerPrepStmts=true&rewriteBatchedStatements=true");
        config.setUsername(System.getenv("DB_USERNAME"));
        config.setPassword(System.getenv("DB_PASSWORD"));
        
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(60000);
        config.setAutoCommit(true);
        config.setConnectionTestQuery("SELECT 1");
        
        return new HikariDataSource(config);
    }


}
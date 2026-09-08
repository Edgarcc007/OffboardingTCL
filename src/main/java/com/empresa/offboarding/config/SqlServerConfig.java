package com.empresa.offboarding.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

@Configuration
public class SqlServerConfig {

    private static final Logger log = LoggerFactory.getLogger(SqlServerConfig.class);

    @Value("${spring.sqlserver.username}")
    private String username;

    @Value("${spring.sqlserver.password}")
    private String password;

    @Bean
    public DataSource sqlServerDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:jtds:sqlserver://10.138.96.98:1433/TCL");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("net.sourceforge.jtds.jdbc.Driver");
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(10000);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);

        // Fix: jTDS no implementa isValid(), usar connectionTestQuery
        config.setConnectionTestQuery("SELECT 1");

        config.addDataSourceProperty("ssl", "off");

        log.info("SqlServer datasource configured with jTDS (no SSL)");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate sqlServerJdbcTemplate() {
        return new JdbcTemplate(sqlServerDataSource());
    }
}
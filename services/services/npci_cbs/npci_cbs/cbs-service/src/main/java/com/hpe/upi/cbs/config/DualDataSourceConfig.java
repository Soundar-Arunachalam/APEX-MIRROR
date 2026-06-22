package com.hpe.upi.cbs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Dual DataSource Configuration for CBS.
 *
 * DEBIT DB  (postgres-debit:5432/cbs_debit)  — records money leaving payer account
 * CREDIT DB (postgres-credit:5432/cbs_credit) — records money arriving in payee account
 *
 * Separate databases ensure:
 * - Debit and credit operations are independently auditable
 * - Failure in credit does not corrupt debit records (auto-reversal needed)
 * - Each bank's CBS can own its own data
 */
@Configuration
public class DualDataSourceConfig {

    @Value("${cbs.debit.db.url}")
    private String debitUrl;

    @Value("${cbs.credit.db.url}")
    private String creditUrl;

    @Value("${cbs.db.username}")
    private String dbUser;

    @Value("${cbs.db.password}")
    private String dbPass;

    @Bean(name = "debitDataSource")
    @Primary
    public DataSource debitDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(debitUrl);
        ds.setUsername(dbUser);
        ds.setPassword(dbPass);
        return ds;
    }

    @Bean(name = "creditDataSource")
    public DataSource creditDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(creditUrl);
        ds.setUsername(dbUser);
        ds.setPassword(dbPass);
        return ds;
    }

    @Bean(name = "debitJdbc")
    @Primary
    public JdbcTemplate debitJdbcTemplate() {
        return new JdbcTemplate(debitDataSource());
    }

    @Bean(name = "creditJdbc")
    public JdbcTemplate creditJdbcTemplate() {
        return new JdbcTemplate(creditDataSource());
    }
}

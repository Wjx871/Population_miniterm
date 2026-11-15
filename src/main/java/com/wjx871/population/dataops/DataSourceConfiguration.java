package com.wjx871.population.dataops;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DataSourceProperties.class, ReplicaProperties.class})
public class DataSourceConfiguration {
    @Bean("primaryDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("replicaDataSource")
    @ConditionalOnProperty(prefix = "population.datasource.replica", name = "enabled", havingValue = "true")
    public HikariDataSource replicaDataSource(ReplicaProperties properties) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new IllegalStateException("REPLICA_DB_URL is required when replica reads are enabled");
        }
        var pool = new HikariDataSource();
        pool.setPoolName("replica");
        pool.setJdbcUrl(properties.getUrl());
        pool.setUsername(properties.getUsername());
        pool.setPassword(properties.getPassword());
        pool.setDriverClassName(properties.getDriverClassName());
        pool.setMaximumPoolSize(properties.getMaximumPoolSize());
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(properties.getConnectionTimeoutMs());
        pool.setValidationTimeout(500);
        pool.setInitializationFailTimeout(-1);
        pool.setReadOnly(true);
        return pool;
    }

    @Bean
    public ReadWriteDataSource routingDataSource(@Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") ObjectProvider<DataSource> replica, ReplicaProperties properties) {
        return new ReadWriteDataSource(primary, replica.getIfAvailable(), properties.getFailureCooldownMs());
    }

    @Bean("dataSource")
    @Primary
    public DataSource dataSource(ReadWriteDataSource routing) {
        return new LazyConnectionDataSourceProxy(routing);
    }
}

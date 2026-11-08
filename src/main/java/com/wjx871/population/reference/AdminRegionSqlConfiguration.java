package com.wjx871.population.reference;

import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MySQL shared locks preserve parallel writes on unrelated branches; H2 uses stronger locks. */
@Configuration(proxyBeanMethods = false)
public class AdminRegionSqlConfiguration {
    @Bean
    DatabaseIdProvider databaseIdProvider() {
        var provider = new VendorDatabaseIdProvider();
        var vendors = new Properties();
        vendors.setProperty("MySQL", "mysql");
        vendors.setProperty("H2", "h2");
        provider.setProperties(vendors);
        return provider;
    }
}

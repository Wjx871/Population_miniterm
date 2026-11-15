package com.wjx871.population.dataops;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("population.datasource.replica")
public class ReplicaProperties {
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private int maximumPoolSize = 5;
    private long connectionTimeoutMs = 1000;
    private long failureCooldownMs = 5000;
}

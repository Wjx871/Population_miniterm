package com.wjx871.population.system;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties("population.rabbitmq")
public class RabbitMqProperties {
    private boolean enabled;
    private String managementUrl = "http://rabbitmq:15672";
    private String username = "";
    private String password = "";
    private String virtualHost = "/";
    private int timeoutMs = 2000;
}

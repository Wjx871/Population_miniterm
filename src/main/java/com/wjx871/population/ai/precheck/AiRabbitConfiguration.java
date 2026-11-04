package com.wjx871.population.ai.precheck;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "population.ai.precheck", name = {"enabled", "rabbit-enabled"}, havingValue = "true")
public class AiRabbitConfiguration {
    @Bean
    Declarables aiPrecheckBrokerTopology(AiPrecheckProperties properties) {
        DirectExchange exchange = new DirectExchange(properties.getExchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(properties.getDeadLetterExchange(), true, false);
        Queue mainQueue = QueueBuilder.durable(properties.getQueue())
                .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey())
                .withArgument("x-max-length", properties.getQueueCapacity())
                .withArgument("x-overflow", "reject-publish")
                .build();
        Queue deadQueue = QueueBuilder.durable(properties.getDeadLetterQueue())
                .withArgument("x-max-length", properties.getQueueCapacity())
                .withArgument("x-overflow", "reject-publish")
                .build();
        Binding mainBinding = BindingBuilder.bind(mainQueue).to(exchange).with(properties.getRoutingKey());
        Binding deadBinding = BindingBuilder.bind(deadQueue).to(deadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
        return new Declarables(exchange, deadLetterExchange, mainQueue, deadQueue, mainBinding, deadBinding);
    }

    @Bean(name = "aiPrecheckListenerContainerFactory")
    SimpleRabbitListenerContainerFactory aiPrecheckListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            AiPrecheckProperties properties, RabbitTemplate rabbitTemplate) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(properties.getConsumerConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConsumerConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        factory.setAutoStartup(true);
        rabbitTemplate.setMandatory(true);
        return factory;
    }
}

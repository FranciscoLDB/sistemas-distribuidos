package com.utfpr.edu.sistemas.distribuidos.ms_notificacao.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private static final String PRODUTOR_ID = "Notificacao";

    public static final String EXCHANGE_NAME = "Promocoes";
    public static final String GATEWAY_QUEUE = "Fila_Notificacao";
    public static final String PROMOCAO_PUBLICADA_ROUTING_KEY = "promocao.publicada";
    public static final String PROMOCAO_DESTAQUE_ROUTING_KEY = "promocao.destaque";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue gatewayQueue() {
        return new Queue(GATEWAY_QUEUE, true);
    }

    @Bean
    public Binding bindingPublicada(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with(PROMOCAO_PUBLICADA_ROUTING_KEY);
    }

    @Bean
    public Binding bindingDestaque(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with(PROMOCAO_DESTAQUE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

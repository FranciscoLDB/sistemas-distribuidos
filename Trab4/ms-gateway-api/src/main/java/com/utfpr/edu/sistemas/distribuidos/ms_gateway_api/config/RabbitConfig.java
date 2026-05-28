package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "Promocoes";
    public static final String GATEWAY_QUEUE = "Fila_Gateway";
    public static final String PROMOCAO_RECEBIDA_ROUTING_KEY = "promocao.recebida";
    public static final String PROMOCAO_VOTO_ROUTING_KEY = "promocao.voto";
    public static final String PROMOCAO_PUBLICADA_ROUTING_KEY = "promocao.publicada";

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
        return BindingBuilder.bind(gatewayQueue).to(exchange).with("promocao.publicada");
    }

    @Bean
    public Binding bindingDestaque(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with("promocao.destaque");
    }

    @Bean
    public Binding bindingCategoria(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with("promocao.categoria.*");
    }

    @Bean
    public Binding bindingHotdeal(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with("notificacao.hotdeal");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

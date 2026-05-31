package com.utfpr.edu.sistemas.distribuidos.ms_promocao.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private static final String PRODUTOR_ID = "Promocao";

    public static final String EXCHANGE_NAME = "Promocoes";
    public static final String GATEWAY_QUEUE = "Fila_Promocao";
    public static final String PROMOCAO_RECEBIDA_ROUTING_KEY = "promocao.recebida";
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
    public Binding bindingRecebida(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with("promocao.recebida");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

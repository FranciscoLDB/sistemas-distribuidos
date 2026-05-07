package com.utfpr.francisco.sistemas.distribuidos.testes;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class Send {

    private String queueName = "hello";
    private static final String EXCHANGE_NAME = "test_exchange";

    public Send () {
        System.out.println("Send class created");
    }

    public Send (String queueName) {
        this.queueName = queueName;
        System.out.println("Send class created with queue: " + queueName);
    }

    public void sendMessage(String message) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");  // Local RabbitMQ host
        factory.setPort(5672);         // Default AMQP port
        // If authentication is required, add: factory.setUsername("guest"); factory.setPassword("guest");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // QUEUE DECLARATION
            // Declare the queue (idempotent, safe to call multiple times)
//            boolean durable = true; // Make sure the queue survives a RabbitMQ restart
//            Map<String, Object> args = new HashMap<>();
//            args.put("x-queue-type", "quorum");
//            channel.queueDeclare(queueName, durable, false, false, args);


            // EXCHANGE DECLARATION
            channel.exchangeDeclare(EXCHANGE_NAME, "direct", true);

            // Publish the message
            channel.basicPublish(EXCHANGE_NAME, "routingKey", null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println("Message sent to " + queueName + ": " + message);

        } catch (IOException | TimeoutException e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

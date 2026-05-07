package com.utfpr.francisco.sistemas.distribuidos.testes;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class Receiver {

    private String queueName = "hello";
    private List<String> topics;
    private static final String EXCHANGE_NAME = "test_exchange";

    public Receiver () {
        System.out.println("Receiver class created");
    }

    public Receiver (String queueName) {
        this.queueName = queueName;
        System.out.println("Receiver class created with queue: " + queueName);
    }

    public Receiver (String queueName, List<String> topics) {
        this.queueName = queueName;
        this.topics = topics;
        System.out.println("Receiver class created with queue: " + queueName + " and topics: " + topics);
    }

    private static void doWork(String task) throws InterruptedException {
        for (char ch: task.toCharArray()) {
            if (ch == '.') Thread.sleep(1000);
        }
    }

    public void startReceiving() throws IOException, TimeoutException, InterruptedException {
        Channel channel = getChannel();
        //String queueName = channel.queueDeclare().getQueue();

        // Clear the terminal screen
        System.out.print("\033[2J\033[H");

        // Display a nice waiting menu
        System.out.println("=====================================");
        System.out.println("       WAITING FOR MESSAGES");
        System.out.println("=====================================");
        System.out.println("Queue: " + queueName);
        System.out.println("Status: Listening...");
        System.out.println("=====================================");
        System.out.println("Incoming messages will appear below.");
        System.out.println("Press Ctrl+C to stop and return to menu.");
        System.out.println("=====================================");


        channel.basicQos(1); // Accept only one unacknowledged message at a time
        // Callback to handle delivered messages
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println("Received: '" + message + "'");
            try {
                doWork(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // Restore interrupted status
            } finally {
                System.out.println(" [x] Done");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false); // Acknowledge the message
            }
        };

        boolean autoAck = false; // auto Acknowledgment
        channel.basicConsume(queueName, autoAck, deliverCallback, consumerTag -> { });

        while (true) {
            // Keep the program running to listen for messages
            Thread.sleep(1000);
        }
    }

    private Channel getChannel() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");  // Local RabbitMQ host
        factory.setPort(5672);         // Default AMQP port
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // EXCHANGE DECLARATION
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");
        //String queueName = channel.queueDeclare().getQueue();

        // Declare the queue (idempotent, safe to call multiple times)
        boolean durable = false; // Make sure the queue survives a RabbitMQ restart
        Map<String, Object> args = new HashMap<>();
        channel.queueDeclare(queueName, durable, false, false, args);

        // Bind the queue to the exchange with the routing key equal to the topics
        for (String topic : topics) {
            channel.queueBind(queueName, EXCHANGE_NAME, topic);
        }

        return channel;
    }
}

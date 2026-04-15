package com.utfpr.francisco.sistemas.distribuidos.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.utfpr.francisco.sistemas.distribuidos.model.Evento;
import com.utfpr.francisco.sistemas.distribuidos.model.Promocao;
import com.utfpr.francisco.sistemas.distribuidos.util.CryptoUtil;
import com.utfpr.francisco.sistemas.distribuidos.util.KeyManager;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsável pelo gerenciamento das promoções no sistema.
 * <p>
 * Esse serviço recebe eventos indicando que novas promoções foram recebidas. Ao receber um evento, o
 * serviço deve inicialmente validar a assinatura digital da mensagem para garantir sua
 * autenticidade e integridade. Quando um evento de promoção é validado, o serviço registra
 * a promoção, assina e publica um novo evento informando que a promoção foi
 * disponibilizada no sistema.
 * </p>
 *
 * O microsserviço Promocao consome os eventos promocao.recebida, assina
 * digitalmente e publica eventos promocao.publicada.
 */
public class PromocaoService {

    private Channel channel;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PRODUTOR_ID = "Promocao";

    private static final String PROMOCAO_QUEUE = "Fila_Promocao";
    private static final String EXCHANGE_NAME = "Promocoes";
    private static final String PROMOCAO_RECEBIDA_ROUTING_KEY = "promocao.recebida";
    private static final String PROMOCAO_PUBLICADA_ROUTING_KEY = "promocao.publicada";

    public PromocaoService() {
    }

    public void start() {
        this.channel = getChannel();
        System.out.println("PromocaoService iniciado e aguardando eventos...");
    }

    private void processMessage(String message) throws Exception {
        Evento evento = objectMapper.readValue(message, Evento.class);

        // Validar assinatura
        if (!validarAssinatura(evento)) {
            System.err.println("❌ EVENTO REJEITADO: Assinatura inválida!");
            return;
        }
        System.out.println("✓ Assinatura válida! Processando evento...");

        // Simular processamento da promoção (pode incluir lógica de negócios aqui)
        System.out.println("Processando promoção: " + evento.getConteudo());
        // Criar evento de promoção publicada
        Evento promocaoPublicada = new Evento();
        promocaoPublicada.setProdutor(PRODUTOR_ID);
        promocaoPublicada.setConteudo(evento.getConteudo()); // Reutiliza o conteúdo original
        promocaoPublicada.setTipo(PROMOCAO_PUBLICADA_ROUTING_KEY);
        // Assinar o evento
        String assinatura = CryptoUtil.sign(promocaoPublicada.getConteudo(), KeyManager.loadPrivateKey(PRODUTOR_ID));
        promocaoPublicada.setAssinatura(assinatura);

        // Publicar o evento de promoção publicada
        String promocaoPublicadaJson = objectMapper.writeValueAsString(promocaoPublicada);
        channel.basicPublish(EXCHANGE_NAME, PROMOCAO_PUBLICADA_ROUTING_KEY, null, promocaoPublicadaJson.getBytes(StandardCharsets.UTF_8));
        System.out.println("✓ Evento de promoção publicada enviado com sucesso!");
    }

    /**
     * Validar assinatura de um evento
     */
    private boolean validarAssinatura(Evento evento) {
        try {
            // Buscar a chave pública do produtor
            PublicKey publicKey = KeyManager.loadPublicKey(evento.getProdutor());

            // Validar a assinatura
            boolean isValid = CryptoUtil.verify(evento.getConteudo(), evento.getAssinatura(), publicKey);

            if (isValid) {
                System.out.println("✓ Assinatura válida do produtor: " + evento.getProdutor());
            } else {
                System.err.println("❌ Assinatura inválida do produtor: " + evento.getProdutor());
            }

            return isValid;

        } catch (Exception e) {
            System.err.println("❌ Erro ao validar assinatura: " + e.getMessage());
            return false;
        }
    }

    private Channel getChannel() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");  // Local RabbitMQ host
            factory.setPort(5672);         // Default AMQP port
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
            Map<String, Object> args = new HashMap<>();
            channel.queueDeclare(PROMOCAO_QUEUE, true, false, false, args);

            channel.queueBind(PROMOCAO_QUEUE, EXCHANGE_NAME, PROMOCAO_RECEBIDA_ROUTING_KEY);

            channel.basicQos(1);  // Process one message at a time
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("\nNova mensagem recebida!");
                try {
                    processMessage(message);
                } catch (Exception e) {
                    System.err.println("Erro ao processar mensagem: " + e.getMessage());
                } finally {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            };
            boolean autoAck = false; // auto Acknowledgment
            channel.basicConsume(PROMOCAO_QUEUE, autoAck, deliverCallback, consumerTag -> {});

            return channel;
        } catch (Exception e) {
            throw new RuntimeException("Failed to establish RabbitMQ channel", e);
        }
    }
}

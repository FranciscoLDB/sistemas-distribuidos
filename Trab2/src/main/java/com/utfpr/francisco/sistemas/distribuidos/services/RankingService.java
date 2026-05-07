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
import java.util.HashMap;
import java.util.Map;

/**
 * Responsável pelo processamento dos votos associados às promoções.
 * Ao receber um evento, o serviço deve inicialmente validar a assinatura digital da
 * mensagem para garantir sua autenticidade e integridade.
 * Para cada evento validado, ele processa o voto (positivo ou negativo) da promoção correspondente,
 * atualiza o contador de votos e recalcula o score de popularidade da promoção específica.
 * Caso o score ultrapasse um limite definido pelo sistema, a promoção deve ser considerada uma
 * promoção em destaque (hot deal). Quando isso ocorrer, o serviço assina e publica um
 * novo evento indicando que a promoção foi destacada.
 * Todos os eventos publicados por esse serviço devem ser assinados digitalmente antes de serem enviados ao RabbitMQ.
 * <p>
 * O ranking consome o evento promocao.voto, assina digitalmente e publica o
 * evento promocao.destaque.
 * </p>
 */
public class RankingService {

    private Channel channel;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PRODUTOR_ID = "Ranking";

    private static final String PROMOCAO_QUEUE = "Fila_Ranking";
    private static final String EXCHANGE_NAME = "Promocoes";
    private static final String PROMOCAO_DESTAQUE_ROUTING_KEY = "promocao.destaque";
    private static final String PROMOCAO_VOTO_ROUTING_KEY = "promocao.voto";

    public RankingService() {
    }

    public void start() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("║" + " ".repeat(58) + "║");
        System.out.println("║" + centerString("SERVICO DE RANKING E DESTAQUE", 58) + "║");
        System.out.println("║" + centerString("Processador de Votos e Identificador de Hot Deals", 58) + "║");
        System.out.println("║" + " ".repeat(58) + "║");
        System.out.println("=".repeat(60));
        System.out.println();
        
        this.channel = getChannel();
        
        System.out.println();
        System.out.println("├─ Status: OPERACIONAL");
        System.out.println("├─ Fila: " + PROMOCAO_QUEUE);
        System.out.println("├─ Exchange: " + EXCHANGE_NAME);
        System.out.println("├─ Modo: Aguardando eventos de voto");
        System.out.println("└─ Timestamp: " + java.time.LocalDateTime.now());
        System.out.println();
        System.out.println("Servico pronto para processar votos e identificar destaques...\n");
    }
    
    private String centerString(String s, int size) {
        if (s.length() >= size) return s;
        int totalPadding = size - s.length();
        int leftPadding = totalPadding / 2;
        return " ".repeat(leftPadding) + s + " ".repeat(totalPadding - leftPadding);
    }

    private void processMessage(String message) throws Exception {
        Evento evento = objectMapper.readValue(message, Evento.class);

        // Validar assinatura
        if (!CryptoUtil.validarAssinatura(evento)) {
            System.err.println("[ERRO] EVENTO REJEITADO: Assinatura invalida!");
            return;
        }
        System.out.println("[VALIDADO] Assinatura do produtor: " + evento.getProdutor());

        Promocao promocao = objectMapper.readValue(evento.getConteudo(), Promocao.class);

        processaVoto(promocao);
    }

    private void processaVoto(Promocao promocao) throws Exception {
        System.out.println("[PROCESSANDO] Voto registrado para: " + promocao.getNomeProduto());
        System.out.println("[INFO] Total de votos: " + promocao.getVotos());

        // Calcula voto
        boolean isDestaque = promocao.getVotos() >= 5;

        if (isDestaque) {
            System.out.println("[DESTAQUE] Promocao identificada como hot deal!");
            promocao.setStatus("DESTAQUE");
            Evento evento = criarEvento(objectMapper.writeValueAsString(promocao));

            String eventoJson = objectMapper.writeValueAsString(evento);
            channel.basicPublish(EXCHANGE_NAME, PROMOCAO_DESTAQUE_ROUTING_KEY, null, eventoJson.getBytes(StandardCharsets.UTF_8));
            System.out.println("[PUBLICADO] Evento: " + PROMOCAO_DESTAQUE_ROUTING_KEY);
        }
    }

    private static Evento criarEvento(String conteudo) throws Exception {
        Evento evento = new Evento(PROMOCAO_DESTAQUE_ROUTING_KEY, conteudo, PRODUTOR_ID);
        String signature = CryptoUtil.sign(conteudo, KeyManager.loadPrivateKey(PRODUTOR_ID));
        evento.setAssinatura(signature);
        return evento;
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

            channel.queueBind(PROMOCAO_QUEUE, EXCHANGE_NAME, PROMOCAO_VOTO_ROUTING_KEY);

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

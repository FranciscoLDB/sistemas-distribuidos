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
 * Responsável por distribuir notificações sobre promoções publicadas no sistema.
 * <p>
 * Esse serviço consome eventos relacionados à publicação de novas promoções e à
 * identificação de promoções em destaque.
 * </p>
 * Ao receber um evento, o serviço deve validar a assinatura digital da
 * mensagem para garantir sua autenticidade e integridade.
 * Após a validação, o serviço identifica a categoria associada à promoção e
 * publica uma notificação correspondente no RabbitMQ.
 * <p>
 * Esse serviço consome os eventos promocao.publicada e promocao.destaque, e
 * publica eventos promocao.categoria1, promocao.categoria2, ..., promocao.categoriaN.
 * </p>
 * Para cada nova promoção em destaque, ele deve publicar um novo evento na categoria
 * correspondente com a palavra "hot deal".
 */
public class NotificacaoService {

    private Channel channel;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PRODUTOR_ID = "Notificacao";

    private static final String NOTIFICACAO_QUEUE = "Fila_Notificacao";
    private static final String EXCHANGE_NAME = "Promocoes";
    private static final String PROMOCAO_PUBLICADA_ROUTING_KEY = "promocao.publicada";
    private static final String PROMOCAO_DESTAQUE_ROUTING_KEY = "promocao.destaque";

    public NotificacaoService() {
    }

    public void start() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("║" + " ".repeat(58) + "║");
        System.out.println("║" + centerString("SERVICO DE NOTIFICACOES", 58) + "║");
        System.out.println("║" + centerString("Processador de Eventos de Promocoes", 58) + "║");
        System.out.println("║" + " ".repeat(58) + "║");
        System.out.println("=".repeat(60));
        System.out.println();
        
        this.channel = getChannel();
        
        System.out.println();
        System.out.println("├─ Status: OPERACIONAL");
        System.out.println("├─ Fila: " + NOTIFICACAO_QUEUE);
        System.out.println("├─ Exchange: " + EXCHANGE_NAME);
        System.out.println("├─ Modo: Aguardando eventos de notificacao");
        System.out.println("└─ Timestamp: " + java.time.LocalDateTime.now());
        System.out.println();
        System.out.println("Servico pronto para processar eventos...\n");
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
        if (!validarAssinatura(evento)) {
            System.err.println("[ERRO] EVENTO REJEITADO: Assinatura invalida!");
            return;
        }
        System.out.println("[VALIDADO] Assinatura do produtor: " + evento.getProdutor());

        Promocao promocao = objectMapper.readValue(evento.getConteudo(), Promocao.class);

        // Processar baseado no tipo de evento
        if (PROMOCAO_PUBLICADA_ROUTING_KEY.equals(evento.getTipo())) {
            processarPromocaoPublicada(promocao);
        } else if (PROMOCAO_DESTAQUE_ROUTING_KEY.equals(evento.getTipo())) {
            processarPromocaoDestaque(promocao);
        }
    }

    private void processarPromocaoPublicada(Promocao promocao) throws Exception {
        System.out.println("[PROCESAR] Promocao publicada recebida: " + promocao.getNomeProduto());

        // Publicar notificação na categoria correspondente
        String categoriaRoutingKey = "promocao." + promocao.getCategoria().toLowerCase().replace(" ", "");
        String notificacaoConteudo = objectMapper.writeValueAsString(promocao);

        publicarNotificacao(categoriaRoutingKey, notificacaoConteudo);
        System.out.println("[PUBLICADO] Categoria: " + promocao.getCategoria());
    }

    private void processarPromocaoDestaque(Promocao promocao) throws Exception {
        System.out.println("[DESTAQUE] Promocao em destaque recebida: " + promocao.getNomeProduto());

        // Publicar notificação "hot deal" na categoria correspondente
        String categoriaRoutingKey = "promocao." + promocao.getCategoria().toLowerCase().replace(" ", "");
        String notificacaoConteudo = "HOT DEAL";

        publicarNotificacao(categoriaRoutingKey, notificacaoConteudo);
        System.out.println("[PUBLICADO] Hot deal para categoria: " + promocao.getCategoria());
    }

    private void publicarNotificacao(String routingKey, String conteudo) throws Exception {
        // Criar evento com assinatura
        Evento evento = criarEvento(routingKey, conteudo);
        String eventoJson = objectMapper.writeValueAsString(evento);

        // Publicar no exchange
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, eventoJson.getBytes(StandardCharsets.UTF_8));
        System.out.println("[ENVIADO] Evento: " + routingKey);
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

            if (!isValid) {
                System.err.println("[ERRO] Assinatura invalida do produtor: " + evento.getProdutor());
            }

            return isValid;

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao validar assinatura: " + e.getMessage());
            return false;
        }
    }

    private Evento criarEvento(String routingKey, String conteudo) throws Exception {
        Evento evento = new Evento(routingKey, conteudo, PRODUTOR_ID);
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
            channel.queueDeclare(NOTIFICACAO_QUEUE, true, false, false, args);

            // Bind para eventos de promoção publicada
            channel.queueBind(NOTIFICACAO_QUEUE, EXCHANGE_NAME, PROMOCAO_PUBLICADA_ROUTING_KEY);

            // Bind para eventos de promoção em destaque
            channel.queueBind(NOTIFICACAO_QUEUE, EXCHANGE_NAME, PROMOCAO_DESTAQUE_ROUTING_KEY);

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
            channel.basicConsume(NOTIFICACAO_QUEUE, autoAck, deliverCallback, consumerTag -> {});

            return channel;
        } catch (Exception e) {
            throw new RuntimeException("Failed to establish RabbitMQ channel", e);
        }
    }
}

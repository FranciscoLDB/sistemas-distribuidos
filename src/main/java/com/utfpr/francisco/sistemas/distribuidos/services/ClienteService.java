package com.utfpr.francisco.sistemas.distribuidos.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.utfpr.francisco.sistemas.distribuidos.model.Promocao;
import org.h2.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * (0,2) Processos Cliente Consumidores de Promoções
 * O processo cliente consumidor é responsável por receber notificações sobre promoções
 * de interesse. Quando um usuário decide seguir uma determinada categoria de produto,
 * ele passa a consumir notificações de promoções desta categoria no RabbitMQ.
 * (0,2) Cada cliente deve manifestar seu interesse em receber notificações de eventos sobre
 * promoções de diferentes categorias e promoções em destaque. Por exemplo, um cliente
 * interessado em promoções de livros, jogos e de promoções em destaque consumirá os
 * eventos promocao.livro, promocao.jogo e promocao.destaque, respectivamente. Ao
 * receber uma mensagem de notificação, esta será exibida no terminal.
 * Para simplificar, as categorias de interesse dos usuários podem estar definidas no código
 * do cliente (hard coded).
 * O sistema deve utilizar uma exchange do tipo direct ou topic no RabbitMQ. Os eventos
 * devem utilizar routing keys hierárquicas, permitindo que consumidores se inscrevam
 * em diferentes categorias de eventos utilizando padrões de binding. Cada cliente pode criar
 * sua própria fila e associá-la às routing keys correspondentes às categorias de interesse.
 */
public class ClienteService {

    private Channel channel;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXCHANGE_NAME = "Promocoes";
    private static final String CLIENTE_QUEUE_PREFIX = "Fila_Cliente_";

    // Todas as categorias disponíveis
    private static final List<String> CATEGORIAS_DISPONIVEIS = Arrays.asList(
            "eletronicos",
            "livros",
            "roupas",
            "alimentos",
            "esportes",
            "beleza",
            "destaque"
    );

    private String clienteId;
    private String clienteQueue;
    private Set<String> categoriasInteresse;
    private Scanner scanner;

    public ClienteService() {
        this.clienteId = "Cliente_" + System.currentTimeMillis();
        this.clienteQueue = CLIENTE_QUEUE_PREFIX + this.clienteId;
        this.categoriasInteresse = new HashSet<>();
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║         CLIENTE CONSUMIDOR DE PROMOÇÕES            ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║ ID do Cliente: " + padRight(this.clienteId, 37) + "║");
        System.out.println("╚════════════════════════════════════════════════════╝");

        // Menu para seleção de categorias
        selecionarCategorias();

        if (categoriasInteresse.isEmpty()) {
            System.out.println("\n❌ Nenhuma categoria selecionada. Encerrando...");
            return;
        }

        this.channel = getChannel();

        System.out.println("\n✓ Conectado ao RabbitMQ");
        System.out.println("✓ Aguardando notificações de promoções...");
        System.out.println("═══════════════════════════════════════════════════════\n");
    }

    private void selecionarCategorias() {
        boolean selecionando = true;

        while (selecionando) {
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║         SELECIONE CATEGORIAS DE INTERESSE          ║");
            System.out.println("╠════════════════════════════════════════════════════╣");

            for (int i = 0; i < CATEGORIAS_DISPONIVEIS.size(); i++) {
                String categoria = CATEGORIAS_DISPONIVEIS.get(i);
                String marcado = categoriasInteresse.contains(categoria) ? "✓" : " ";
                System.out.printf("║ [%s] %d. %-44s║%n", marcado, (i + 1), categoria.toUpperCase());
            }

            System.out.println("║                                                    ║");
            System.out.println("║ 0. Confirmar seleção                               ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.print("Escolha uma opção (número ou 0 para confirmar): ");

            try {
                int choice = scanner.nextInt();
                scanner.nextLine();

                if (choice == 0) {
                    if (categoriasInteresse.isEmpty()) {
                        System.out.println("\n❌ Você deve selecionar pelo menos uma categoria!");
                        continue;
                    }
                    selecionando = false;
                } else if (choice > 0 && choice <= CATEGORIAS_DISPONIVEIS.size()) {
                    String categoria = CATEGORIAS_DISPONIVEIS.get(choice - 1);
                    if (categoriasInteresse.contains(categoria)) {
                        categoriasInteresse.remove(categoria);
                        System.out.println("✓ Categoria '" + categoria + "' removida.");
                    } else {
                        categoriasInteresse.add(categoria);
                        System.out.println("✓ Categoria '" + categoria + "' adicionada.");
                    }
                } else {
                    System.out.println("❌ Opção inválida!");
                }
            } catch (Exception e) {
                scanner.nextLine();
                System.out.println("❌ Entrada inválida!");
            }
        }

        System.out.println("\n✓ Categorias selecionadas:");
        for (String cat : categoriasInteresse) {
            System.out.println("   • " + cat.toUpperCase());
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
            channel.queueDeclare(clienteQueue, true, false, false, args);

            // Bind para cada categoria de interesse selecionada
            System.out.println("\n📢 Inscrevendo em categorias...");
            for (String categoria : categoriasInteresse) {
                String routingKey = "promocao." + categoria;
                channel.queueBind(clienteQueue, EXCHANGE_NAME, routingKey);
                System.out.println("✓ Inscrito em: " + routingKey);
            }

            channel.basicQos(1);  // Process one message at a time

            // Callback para processar mensagens recebidas
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                String routingKey = delivery.getEnvelope().getRoutingKey();

                if (routingKey.equals("promocao.destaque")) {
                    // Notificação de promoção em destaque
                    exibirNotificacaoDestaque(message);
                } else {
                    // Notificação de promoção por categoria
                    exibirNotificacaoCategoria(message, routingKey);
                }

                try {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    System.err.println("Erro ao confirmar mensagem: " + e.getMessage());
                }
            };

            boolean autoAck = false;
            channel.basicConsume(clienteQueue, autoAck, deliverCallback, consumerTag -> {});

            return channel;
        } catch (Exception e) {
            throw new RuntimeException("Failed to establish RabbitMQ channel", e);
        }
    }

    private static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

    public void exibirMenu() {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║            MENU DO CLIENTE                         ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║ 1. Listar categorias de interesse                  ║");
        System.out.println("║ 2. Voltar ao menu principal                        ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.print("Escolha uma opção: ");
    }

    public boolean handleMenuChoice(int choice, Scanner scanner) {
        return switch (choice) {
            case 1 -> {
                listarCategorias();
                yield true;
            }
            case 2 -> false;
            default -> {
                System.out.println("❌ Opção inválida. Por favor, tente novamente.");
                yield true;
            }
        };
    }

    private void listarCategorias() {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║     CATEGORIAS DE INTERESSE DO CLIENTE              ║");
        System.out.println("╠════════════════════════════════════════════════════╣");

        int index = 1;
        for (String cat : categoriasInteresse) {
            System.out.printf("║ %d. %-48s║%n", index, cat.toUpperCase());
            index++;
        }

        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println("\n✓ Total de categorias: " + categoriasInteresse.size());
    }

    private void exibirNotificacaoDestaque(String message) {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║           🔥 PROMOÇÃO EM DESTAQUE 🔥              ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║ " + padRight(message, 50) + "║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");
    }

    private void exibirNotificacaoCategoria(String message, String routingKey) {
        try {
            Promocao promocao = objectMapper.readValue(message, Promocao.class);

            // Exibir mensagem formatada baseada no GatewayService
            System.out.println("\n╔════════════════════════════════════╗");
            System.out.println("║    NOVA PROMOÇÃO DISPONÍVEL        ║");
            System.out.println("╠════════════════════════════════════╣");
            System.out.println("║ Nome do produto: " + padRight(promocao.getNomeProduto(), 18) + "║");
            System.out.println("║ Categoria: " + padRight(promocao.getCategoria(), 24) + "║");
            System.out.println("║ Preço Original: R$ " + padRight(String.format("%.2f", promocao.getPrecoOriginal()), 16) + "║");
            System.out.println("║ Preço Promocional: R$ " + padRight(String.format("%.2f", promocao.getPrecoPromocional()), 13) + "║");
            double desconto = ((promocao.getPrecoOriginal() - promocao.getPrecoPromocional()) / promocao.getPrecoOriginal()) * 100;
            System.out.println("║ Desconto: " + padRight(String.format("%.1f%%", desconto), 25) + "║");
            System.out.println("╚════════════════════════════════════╝\n");

        } catch (Exception e) {
            // Fallback para mensagem simples se não conseguir desserializar
            System.out.println("\n┌────────────────────────────────────────────────────┐");
            System.out.println("│           NOVA NOTIFICAÇÃO RECEBIDA                │");
            System.out.println("├────────────────────────────────────────────────────┤");
            System.out.println("│ Tipo: " + padRight(routingKey, 40) + "│");
            String msgPreview = message.substring(0, Math.min(36, message.length()));
            System.out.println("│ Mensagem: " + padRight(msgPreview, 36) + "│");
            System.out.println("└────────────────────────────────────────────────────┘\n");
        }
    }
}

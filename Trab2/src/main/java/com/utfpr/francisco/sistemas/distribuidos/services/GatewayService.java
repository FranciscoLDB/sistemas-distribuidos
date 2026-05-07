package com.utfpr.francisco.sistemas.distribuidos.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.utfpr.francisco.sistemas.distribuidos.database.PromocaoRepository;
import com.utfpr.francisco.sistemas.distribuidos.model.Evento;
import com.utfpr.francisco.sistemas.distribuidos.model.Promocao;
import com.utfpr.francisco.sistemas.distribuidos.util.CryptoUtil;
import com.utfpr.francisco.sistemas.distribuidos.util.KeyManager;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.*;

import java.security.KeyPair;

/**
 * Responsável por realizar a interação com os usuários (clientes e lojas) por meio
 * do terminal.
 * <p>
 * Ele apresenta as opções do sistema e permite que os usuários executem ações
 * como cadastrar novas promoções, listar promoções publicadas (validadas pelo MS
 * Promocao) e votar em promoções existentes.
 * </p>
 * Esse serviço funciona como o ponto de
 * entrada do sistema e transforma as ações realizadas pelos usuários em eventos que serão
 * enviados para o broker RabbitMQ.
 * <p>
 * Sempre que um evento for publicado, o gateway deve gerar a assinatura digital
 * da mensagem utilizando sua chave privada e incluí-la no envelope do evento.
 * </p>
 * <p>
 * O gateway publica eventos promocao.recebida, promocao.voto. Esse serviço
 * consome os eventos promocao.publicada e mantém uma lista local de promoções
 * validadas, permitindo que os usuários listem apenas promoções já aprovadas pelo
 * microsserviço Promocao.
 * </p>
 */
public class GatewayService {

    private static Scanner scanner;
    private static Channel channel;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static PromocaoRepository repository = new PromocaoRepository();

    private static final String PRODUTOR_ID = "Gateway";

    private static final String GATEWAY_QUEUE = "Fila_Gateway";
    private static final String EXCHANGE_NAME = "Promocoes";
    private static final String PROMOCAO_RECEBIDA_ROUTING_KEY = "promocao.recebida";
    private static final String PROMOCAO_VOTO_ROUTING_KEY = "promocao.voto";
    private static final String PROMOCAO_PUBLICADA_ROUTING_KEY = "promocao.publicada";

    public GatewayService() {
        scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("║" + " ".repeat(58) + "║");
        System.out.println("║" + centerString("SERVICO GATEWAY - ENTRADA DO SISTEMA", 58) + "║");
        System.out.println("║" + centerString("Interacao com Usuarios e Gerenciamento de Promocoes", 58) + "║");
        System.out.println("║" + " ".repeat(58) + "║");
        System.out.println("=".repeat(60));
        System.out.println();
        
        channel = getChannel();
        
        System.out.println();
        System.out.println("├─ Status: OPERACIONAL");
        System.out.println("├─ Fila: " + GATEWAY_QUEUE);
        System.out.println("├─ Exchange: " + EXCHANGE_NAME);
        System.out.println("├─ Modo: Aguardando interacao do usuario");
        System.out.println("└─ Timestamp: " + java.time.LocalDateTime.now());
        System.out.println();

        while (true) {
            int choice = displayMenu();

            if (!handleMenuChoice(choice)) {
                break;
            }
        }

        PromocaoRepository.fechar();
    }

    private String centerString(String s, int size) {
        if (s.length() >= size) return s;
        int totalPadding = size - s.length();
        int leftPadding = totalPadding / 2;
        return " ".repeat(leftPadding) + s + " ".repeat(totalPadding - leftPadding);
    }

    public void printMenu() {
        System.out.println("╔════════════════════════════════════╗");
        System.out.println("║           MENU PRINCIPAL           ║");
        System.out.println("╠════════════════════════════════════╣");
        System.out.println("║ 1. Cadastrar nova promoção         ║");
        System.out.println("║ 2. Listar promoções publicadas     ║");
        System.out.println("║ 3. Votar em promoção existente     ║");
        System.out.println("║ 4. Sair                            ║");
        System.out.println("╚════════════════════════════════════╝");
        System.out.print("Escolha uma opção: ");
    }

    public int displayMenu() {
        System.out.println("\n"); // Clear the terminal screen
        printMenu();
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume the newline
        return choice;
    }

    private static boolean handleMenuChoice(int choice) {
        return switch (choice) {
            case 1 -> {
                createPromotion();
                yield true;
            }
            case 2 -> {
                listPromotions();
                yield true;
            }
            case 3 -> {
                voteOnPromotion();
                yield true;
            }
            case 4 -> false;
            default -> {
                System.out.println("Opção inválida. Por favor, tente novamente.");
                yield true;
            }
        };
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
            channel.queueDeclare(GATEWAY_QUEUE, true, false, false, args);

            channel.queueBind(GATEWAY_QUEUE, EXCHANGE_NAME, PROMOCAO_PUBLICADA_ROUTING_KEY);

            channel.basicQos(1); // Accept only one unacknowledged message at a time
            // Callback to handle delivered messages
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("\nNova mensagem recebida!");
                try {
                    processReceivedMessage(message);
                } catch (Exception e) {
                    System.err.println("Error processing message: " + e.getMessage());
                    printMenu();
                } finally {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false); // Acknowledge the message
                    printMenu();
                }
            };
            boolean autoAck = false; // auto Acknowledgment
            channel.basicConsume(GATEWAY_QUEUE, autoAck, deliverCallback, consumerTag -> { });

            return channel;
        } catch (Exception e) {
            System.err.println("Error creating channel: " + e.getMessage());
            return null;
        }
    }

    private void processReceivedMessage(String message) {
        try {
            Evento evento = objectMapper.readValue(message, Evento.class);

            // Validar assinatura
            if (!validarAssinatura(evento)) {
                System.err.println(" EVENTO REJEITADO: Assinatura inválida!");
                return;
            }
            System.out.println(" Assinatura válida! Processando evento...");

            Promocao promocao = objectMapper.readValue(evento.getConteudo(), Promocao.class);

            // Salvar no repositório
            repository.salvar(promocao);

            // Exibir mensagem formatada
            System.out.println("\n╔════════════════════════════════════╗");
            System.out.println("║    PROMOÇÃO PUBLICADA COM SUCESSO  ║");
            System.out.println("╠════════════════════════════════════╣");
            System.out.println("║ Nome do produto: " + padRight(promocao.getNomeProduto(), 18) + "║");
            System.out.println("║ Categoria: " + padRight(promocao.getCategoria(), 24) + "║");
            System.out.println("║ Preço Original: R$ " + padRight(String.format("%.2f", promocao.getPrecoOriginal()), 16) + "║");
            System.out.println("║ Preço Promocional: R$ " + padRight(String.format("%.2f", promocao.getPrecoPromocional()), 13) + "║");
            System.out.println("╚════════════════════════════════════╝\n");

        } catch (Exception e) {
            System.err.println("Erro ao processar promoção recebida: " + e.getMessage());
        }
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

    private static void createPromotion() {
        System.out.print("\033[2J\033[H"); // Clear the terminal screen
        System.out.println("╔════════════════════════════════════╗");
        System.out.println("║         CADASTRAR PROMOÇÃO         ║");
        System.out.println("╠════════════════════════════════════╣");
        System.out.print("║ Nome do Produto: ");
        String nomeProduto = scanner.nextLine().trim();
        System.out.print("║ Categoria: ");
        String categoria = scanner.nextLine().trim();
        System.out.print("║ Preço original: ");
        double precoOriginal;
        try {
            precoOriginal = Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("╚════════════════════════════════════╝");
            System.err.println("Erro: Valor inválido.");
            return;
        }
        System.out.print("║ Preço promocional: ");
        double precoPromocional;
        try {
            precoPromocional = Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("╚════════════════════════════════════╝");
            System.err.println("Erro: Valor inválido.");
            return;
        }
        System.out.println("╚════════════════════════════════════╝");

        // Validate inputs
        if (nomeProduto.isEmpty() || categoria.isEmpty() || precoOriginal <= 0 || precoPromocional <= 0 || precoPromocional > precoOriginal) {
            System.err.println("Erro: Verifique os dados inseridos.");
            return;
        }

        // Create promotion with unique UUID
        String promotionId = UUID.randomUUID().toString().substring(0, 8); // Shorten for display
        Promocao promocao = new Promocao();
        promocao.setId(promotionId);
        promocao.setNomeProduto(nomeProduto);
        promocao.setCategoria(categoria);
        promocao.setPrecoOriginal(precoOriginal);
        promocao.setPrecoPromocional(precoPromocional);
        promocao.setStatus("PENDENTE");

        // Publish promotion to RabbitMQ
        boolean success = publishPromotion(promocao);

        if (success) {
            System.out.println("\n Promoção cadastrada com sucesso!");
            System.out.println("╔════════════════════════════════════╗");
            System.out.println("║       PROMOÇÃO EM VALIDAÇÃO        ║");
            System.out.println("╠════════════════════════════════════╣");
            System.out.println("║ ID: " + padRight(promotionId.substring(0, 8), 31) + "║");
            System.out.println("║ Nome Produto: " + padRight(nomeProduto, 21) + "║");
            System.out.println("║ Categoria: " + padRight(categoria, 24) + "║");
            System.out.println("║ De R$ " + padRight(String.format("%.2f", precoOriginal) + " por R$ " + String.format("%.2f", precoPromocional) + "  ", 29) + "║");
            System.out.println("╚════════════════════════════════════╝");
        } else {
            System.err.println("Erro ao publicar promoção no broker.");
        }

        System.out.print("\nPressione Enter para voltar ao menu...");
        scanner.nextLine();
    }

    private static boolean publishPromotion(Promocao promocao) {
        try {
            String promotionJson = objectMapper.writeValueAsString(promocao);

            // Criar evento com assinatura
            Evento evento = criarEvento(PROMOCAO_RECEBIDA_ROUTING_KEY, promotionJson);
            String message = objectMapper.writeValueAsString(evento);
            channel.basicPublish(
                    EXCHANGE_NAME,
                    PROMOCAO_RECEBIDA_ROUTING_KEY,
                    null,
                    message.getBytes(StandardCharsets.UTF_8)
            );

            System.out.println("[PUBLICADO] Evento: " + PROMOCAO_RECEBIDA_ROUTING_KEY);
            return true;
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao publicar promocao: " + e.getMessage());
            return false;
        }
    }

    private static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

    private static void listPromotions() {
        System.out.print("\033[2J\033[H"); // Clear screen

        List<Promocao> promocoes = repository.obterTodas();

        if (promocoes.isEmpty()) {
            System.out.println("╔════════════════════════════════════╗");
            System.out.println("║   NENHUMA PROMOÇÃO PUBLICADA       ║");
            System.out.println("╚════════════════════════════════════╝");
        } else {
            System.out.println("╔════════════════════════════════════╗");
            System.out.println("║    PROMOÇÕES PUBLICADAS (" + String.format("%02d", promocoes.size()) + ")       ║");
            System.out.println("╠════════════════════════════════════╣");

            int index = 1;
            for (Promocao p : promocoes) {
                System.out.printf("║ %d. %-31s ║%n", index, p.getNomeProduto());
                System.out.printf("║    R$ %.2f → R$ %.2f             ║%n",
                        p.getPrecoOriginal(), p.getPrecoPromocional());
                System.out.printf("║    Votos: %d%24s║%n", p.getVotos(), "");
                System.out.println("╠════════════════════════════════════╣");
                index++;
            }
        }

        System.out.print("Pressione Enter para voltar ao menu...");
        scanner.nextLine();
    }


    private static void voteOnPromotion() {
        System.out.print("\033[2J\033[H"); // Clear screen

        List<Promocao> promocoes = repository.obterTodas();

        if (promocoes.isEmpty()) {
            System.out.println("╔════════════════════════════════════╗");
            System.out.println("║   NENHUMA PROMOÇÃO DISPONÍVEL      ║");
            System.out.println("╚════════════════════════════════════╝");
            System.out.print("Pressione Enter para voltar...");
            scanner.nextLine();
            return;
        }

        System.out.println("╔════════════════════════════════════╗");
        System.out.println("║    VOTAR EM PROMOÇÃO               ║");
        System.out.println("╠════════════════════════════════════╣");

        int index = 1;
        for (Promocao p : promocoes) {
            System.out.printf("║ %d. %-31s ║%n", index, p.getNomeProduto());
            index++;
        }
        System.out.println("║ 0. Voltar                          ║");
        System.out.println("╚════════════════════════════════════╝");

        System.out.print("Escolha uma promoção para votar: ");
        int escolha = scanner.nextInt();
        scanner.nextLine();

        if (escolha > 0 && escolha <= promocoes.size()) {
            Promocao selecionada = promocoes.get(escolha - 1);



            // Publicar evento de voto
            boolean success = publishVote(selecionada);

            if (success) {
                System.out.println(" Voto registrado e evento publicado!");
                System.out.println("Promoção: " + selecionada.getNomeProduto());
            } else {
                System.err.println("Erro ao publicar voto.");
            }
        } else if (escolha != 0) {
            System.out.println("Opção inválida.");
        }

        System.out.print("Pressione Enter para voltar ao menu...");
        scanner.nextLine();
    }

    private static Evento criarEvento(String routingKey, String conteudo) throws Exception {
        Evento evento = new Evento(routingKey, conteudo, PRODUTOR_ID);
        String signature = CryptoUtil.sign(conteudo, KeyManager.loadPrivateKey(PRODUTOR_ID));
        evento.setAssinatura(signature);
        return evento;
    }

    private static boolean publishVote(Promocao promocao) {
        try {
            GatewayService gatewayService = new GatewayService();

            promocao.setVotos(promocao.getVotos() + 1);
            String promocaoJson = objectMapper.writeValueAsString(promocao);

            // Criar evento com assinatura
            Evento evento = criarEvento(PROMOCAO_VOTO_ROUTING_KEY, promocaoJson);
            String mensagem = objectMapper.writeValueAsString(evento);
            gatewayService.channel.basicPublish(
                    EXCHANGE_NAME,
                    PROMOCAO_VOTO_ROUTING_KEY,
                    null,
                    mensagem.getBytes(StandardCharsets.UTF_8)
            );

            System.out.println("[PUBLICADO] Evento: " + PROMOCAO_VOTO_ROUTING_KEY);
            return true;
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao publicar voto: " + e.getMessage());
            return false;
        }
    }


}

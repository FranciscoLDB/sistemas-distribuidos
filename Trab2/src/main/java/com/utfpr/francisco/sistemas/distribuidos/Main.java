package com.utfpr.francisco.sistemas.distribuidos;

import com.utfpr.francisco.sistemas.distribuidos.services.GatewayService;
import com.utfpr.francisco.sistemas.distribuidos.services.PromocaoService;
import com.utfpr.francisco.sistemas.distribuidos.services.RankingService;
import com.utfpr.francisco.sistemas.distribuidos.services.NotificacaoService;
import com.utfpr.francisco.sistemas.distribuidos.services.ClienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

/**
 * Desenvolver um sistema distribuído baseado em microsserviços para gerenciamento e
 * divulgação de promoções de produtos. O sistema deve seguir uma arquitetura
 * orientada a eventos (Event-Driven Architecture), na qual os microsserviços se
 * comunicam exclusivamente através de eventos publicados e consumidos em/de um
 * broker RabbitMQ. Cada microsserviço deverá atuar de forma independente e
 * desacoplada. Então, não é permitido realizar chamadas diretas entre os microsserviços.
 * Usuários podem cadastrar promoções, votar em promoções cadastradas e receber
 * notificações sobre promoções de seu interesse. Promoções com grande quantidade de
 * votos positivos (estabelecer um limite) devem ser destacadas como promoções em
 * destaque (hot deal).
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static Scanner scanner;

    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
        scanner = new Scanner(System.in);

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║ SISTEMA DISTRIBUÍDO DE GERENCIAMENTO DE PROMOÇÕES ║");
        System.out.println("╚═══════════════════════════════════════════════════╝");

        while (true) {
            displayServiceMenu();
            int choice = getUserChoice();

            if (!handleServiceChoice(choice)) {
                break;  // Exit the loop if user chooses to exit
            }
        }

        scanner.close();
        System.out.println("\n✓ Programa encerrado. Até logo!");
    }

    /**
     * Display the main service menu options
     */
    private static void displayServiceMenu() {
        System.out.println("\033[2J\033[H");
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║           ESCOLHA O SERVIÇO A EXECUTAR             ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  1. Gateway       - Interação com usuários         ║");
        System.out.println("║  2. Promocao      - Validação de promoções         ║");
        System.out.println("║  3. Ranking       - Gerenciamento de rankings      ║");
        System.out.println("║  4. Notificacao   - Sistema de notificações        ║");
        System.out.println("║  5. Cliente       - Consumidor de promoções        ║");
        System.out.println("║  6. Sair                                           ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.print("Escolha uma opção: ");
    }

    /**
     * Get user's menu choice
     * @return the chosen option
     */
    private static int getUserChoice() {
        try {
            int choice = scanner.nextInt();
            scanner.nextLine();  // Consume the newline
            return choice;
        } catch (Exception e) {
            scanner.nextLine();  // Clear invalid input
            return -1;
        }
    }

    /**
     * Handle the user's service choice
     * @param choice the selected option
     * @return false if the user wants to exit, true otherwise
     */
    private static boolean handleServiceChoice(int choice) {
        return switch (choice) {
            case 1 -> handleGatewayService();
            case 2 -> handlePromocaoService();
            case 3 -> handleRankingService();
            case 4 -> handleNotificacaoService();
            case 5 -> handleClienteService();
            case 6 -> {
                handleExit();
                yield false;
            }
            default -> {
                System.out.println("❌ Opção inválida. Por favor, tente novamente.");
                yield true;
            }
        };
    }

    /**
     * Handle Gateway Service startup
     */
    private static boolean handleGatewayService() {
        try {
            System.out.println("\n🚀 Iniciando Gateway Service...\n");
            GatewayService gatewayService = new GatewayService();
            gatewayService.start();
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar Gateway Service: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    /**
     * Handle Promocao Service startup
     */
    private static boolean handlePromocaoService() {
        try {
            System.out.println("\n🚀 Iniciando Promocao Service...\n");
            PromocaoService promocaoService = new PromocaoService();
            promocaoService.start();

            System.out.println("\n(Pressione Ctrl+C para retornar ao menu principal)");
            Thread.currentThread().join();
            return true;
        } catch (InterruptedException e) {
            System.out.println("\n✓ Promocao Service encerrado.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar Promocao Service: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    /**
     * Handle Ranking Service startup
     */
    private static boolean handleRankingService() {
        try {
            System.out.println("\n🚀 Iniciando Ranking Service...\n");
            RankingService rankingService = new RankingService();
            rankingService.start();

            System.out.println("\n(Pressione Ctrl+C para retornar ao menu principal)");
            Thread.currentThread().join();
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar Ranking Service: " + e.getMessage());
            return true;
        }
    }

    /**
     * Handle Notificacao Service startup
     */
    private static boolean handleNotificacaoService() {
        try {
            System.out.println("\n🚀 Iniciando Notificacao Service...\n");
            NotificacaoService notificacaoService = new NotificacaoService();
            notificacaoService.start();

            System.out.println("\n(Pressione Ctrl+C para retornar ao menu principal)");
            Thread.currentThread().join();
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar Notificacao Service: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    /**
     * Handle Cliente Service startup
     */
    private static boolean handleClienteService() {
        try {
            System.out.println("\n🚀 Iniciando Cliente Service...\n");
            ClienteService clienteService = new ClienteService();
            clienteService.start();

            while (true) {
                clienteService.exibirMenu();
                int choice = getUserChoice();

                if (!clienteService.handleMenuChoice(choice, scanner)) {
                    break;
                }
            }

            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar Cliente Service: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    /**
     * Handle exit operation
     */
    private static void handleExit() {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║              ENCERRANDO O PROGRAMA                 ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
    }
}
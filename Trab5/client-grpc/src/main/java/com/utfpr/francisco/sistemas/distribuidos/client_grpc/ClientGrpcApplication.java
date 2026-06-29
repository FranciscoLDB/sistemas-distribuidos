package com.utfpr.francisco.sistemas.distribuidos.client_grpc;

import com.utfpr.francisco.sistemas.distribuidos.client_grpc.service.ClientService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import trab5rpc.LogRequest;

import java.util.Scanner;

@SpringBootApplication
public class ClientGrpcApplication implements CommandLineRunner {

    private final ClientService clientService;

    public ClientGrpcApplication(ClientService clientService) {
        this.clientService = clientService;
    }

	public static void main(String[] args) {
		SpringApplication.run(ClientGrpcApplication.class, args);
	}

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        boolean rodando = true;

        System.out.println("====== CLIENTE gRPC INICIADO ======");

        while (rodando) {
            System.out.println("\n----------------------------------");
            System.out.println("- Host atual:"+ clientService.getConnectedHost() +"      -");
            System.out.println("----------------------------------");
            System.out.println("Escolha uma opção:");
            System.out.println("1 - Enviar Comando");
            System.out.println("2 - Solicitar Logs");
            System.out.println("3 - Mudar porta");
            System.out.println("0 - Sair");
            System.out.print("Opção: ");

            String opcao = scanner.nextLine();

            switch (opcao) {
                case "1":
                    System.out.print("Digite o comando para enviar ao servidor: ");
                    String comando = scanner.nextLine();

                    System.out.println("Enviando comando...");
                    var respostaComando = clientService.requestCommand(comando);
                    System.out.println("Resposta do Servidor: " + respostaComando);
                    break;

                case "2":
                    System.out.println("Solicitando logs...");
                    LogRequest logRequest = LogRequest.newBuilder().build();

                    var respostaLog = clientService.request_log(logRequest);
                    System.out.println("Logs recebidos: " + respostaLog);
                    break;

                case "3":
                    System.out.print("Digite a porta nova: ");
                    String porta = scanner.nextLine();
                    clientService.inicializarCanal(Integer.parseInt(porta));
                    break;

                case "0":
                    System.out.println("Encerrando a aplicação...");
                    rodando = false;
                    break;

                default:
                    System.out.println("Opção inválida! Tente novamente.");
                    break;
            }
        }

        scanner.close();
        System.exit(0);
    }

}

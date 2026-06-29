package com.utfpr.francisco.sistemas.distribuidos.client_grpc.service;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;
import trab5rpc.ApiServiceGrpc;
import trab5rpc.CommandRequest;
import trab5rpc.CommandReply;
import trab5rpc.LogRequest;
import trab5rpc.LogReply;

import java.util.concurrent.TimeUnit;

@Service
public class ClientService {

    private final int[] PORTAS = {6001, 6002, 6003, 6004};
    private int indicePortaAtual = 0;

    private ManagedChannel channel;
    private ApiServiceGrpc.ApiServiceBlockingStub apiServiceBlockingStub;

    public ClientService() {
        inicializarCanal(PORTAS[indicePortaAtual]);
    }

    public String getConnectedHost(){
        return "localhost:" + PORTAS[indicePortaAtual];
    }

    public synchronized void inicializarCanal(int porta) {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[gRPC] Conectando no servidor local na porta: " + porta);
        this.channel = ManagedChannelBuilder.forAddress("localhost", porta)
                .usePlaintext()
                .build();
        indicePortaAtual = buscarIndexDaPorta(porta);

        this.apiServiceBlockingStub = ApiServiceGrpc.newBlockingStub(channel);
    }

    private void rotacionarPorta() {
        indicePortaAtual = (indicePortaAtual + 1) % PORTAS.length;
        inicializarCanal(PORTAS[indicePortaAtual]);
    }

    public CommandReply requestCommand(String command) {
        int tentativas = 0;

        while (tentativas < PORTAS.length) {
            try {
                CommandRequest request = CommandRequest.newBuilder()
                        .setCommand(command)
                        .build();
                CommandReply reply = apiServiceBlockingStub.requestCommand(request);
                if (!reply.getSuccess()){
                    System.out.println("Servidor " + PORTAS[indicePortaAtual] + " nao e lider!");
                    int portaLider = Integer.parseInt(reply.getLeaderUrl().split(":")[1]);
                    inicializarCanal(portaLider);
                    reply = apiServiceBlockingStub.requestCommand(request);
                }

                return reply;
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                    System.err.println("[ERRO] Servidor na porta " + PORTAS[indicePortaAtual] + " caiu ou está inacessível.");
                    tentativas++;

                    if (tentativas < PORTAS.length) {
                        rotacionarPorta();
                    }
                } else {
                    System.err.println("[ERRO] Servidor na porta " + PORTAS[indicePortaAtual] + " erro desconhecido:\n " + e.getMessage());
                    throw e;
                }
            }
        }

        System.err.println("[CRÍTICO] Todos os servidores gRPC (6001-6004) estão offline!");

        return CommandReply.newBuilder().setSuccess(false).setLeaderUrl("NENHUM DISPONÍVEL").build();
    }

    public LogReply request_log(LogRequest request) {
        int tentativas = 0;

        while (tentativas < PORTAS.length) {
            try {
                return apiServiceBlockingStub.requestLog(request);
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                    System.err.println("[ERRO] Servidor na porta " + PORTAS[indicePortaAtual] + " caiu ou está inacessível.");
                    tentativas++;

                    if (tentativas < PORTAS.length) {
                        rotacionarPorta();
                    }
                } else {
                    throw e;
                }
            }
        }

        System.err.println("[CRÍTICO] Todos os servidores gRPC (6001-6004) estão offline!");
        return LogReply.newBuilder().build(); // Retorna lista vazia caso tudo falhe
    }

    private int buscarIndexDaPorta(int porta) {
        for (int i = 0; i < PORTAS.length; i++) {
            if (PORTAS[i] == porta) {
                return i;
            }
        }
        return 0;
    }
}

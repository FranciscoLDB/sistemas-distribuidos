//package com.utfpr.francisco.sistemas.distribuidos.client_grpc;
//
//import io.grpc.ManagedChannel;
//import io.grpc.ManagedChannelBuilder;
//import org.springframework.stereotype.Component;
//import java.util.concurrent.TimeUnit;
//import trab5rpc.ApiServiceGrpc.ApiServiceBlockingStub;
//import trab5rpc.ApiServiceGrpc;
//
//@Component
//public class DynamicGrpcClientManager {
//
//    private final int[] PORTAS = {6001, 6002, 6003, 6004};
//    private int indicePortaAtual = 0;
//
//    private String host = "localhost";
//    private int port = 6001;
//
//    private ManagedChannel channel;
//    private ApiServiceBlockingStub apiServiceBlockingStub;
//
//    public DynamicGrpcClientManager() {
//        initChannel(PORTAS[indicePortaAtual]);
//    }
//
//    // Inicializa ou reinicializa o canal gRPC
//    private synchronized void initChannel(int porta) {
//        if (channel != null) {
//            try {
//                channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        System.out.println("[gRPC] Conectando no servidor local na porta: " + porta);
//        this.channel = ManagedChannelBuilder.forAddress("localhost", porta)
//                .usePlaintext()
//                .build();
//
//        this.apiServiceBlockingStub = ApiServiceGrpc.newBlockingStub(channel);
//    }
//
//    public synchronized void mudarPorta(int novaPorta) {
//        System.out.println("Alterando porta gRPC de " + this.port + " para " + novaPorta + "...");
//        this.port = novaPorta;
//        initChannel();
//        System.out.println("Canal gRPC reiniciado com sucesso na porta " + novaPorta);
//    }
//
//    public ApiServiceBlockingStub getStub() {
//        return this.blockingStub;
//    }
//}

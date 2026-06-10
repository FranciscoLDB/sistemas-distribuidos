package com.utfpr.edu.sistemas.distribuidos.ms_promocao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventConsumer {

    @Autowired
    private ObjectMapper objectMapper;

//    @Autowired
//    private SseService sseService; // Vamos criar este serviço para o SSE

//    @RabbitListener(queues = RabbitConfig.GATEWAY_QUEUE)
//    public void receiveMessage(Evento evento) {
//        try {
//            // Reutilizando sua lógica de assinatura
//            if (!validarAssinatura(evento)) {
//                System.err.println("Assinatura Inválida!");
//                return;
//            }
//
//            String routingKey = evento.getRoutingKey();
//            Promocao promocao = objectMapper.readValue(evento.getConteudo(), Promocao.class);
//
//            // 1. Salvar no Banco (Substituindo o repository antigo)
//            // promotionRepository.save(promocao);
//
//            // 2. Enviar via SSE (Notificação em tempo real)
//            sseService.enviarNotificacao(promocao, routingKey);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private boolean validarAssinatura(Evento evento) {
//        try {
//            PublicKey publicKey = KeyManager.loadPublicKey(evento.getProdutor());
//            return CryptoUtil.verify(evento.getConteudo(), evento.getAssinatura(), publicKey);
//        } catch (Exception e) {
//            return false;
//        }
//    }
}

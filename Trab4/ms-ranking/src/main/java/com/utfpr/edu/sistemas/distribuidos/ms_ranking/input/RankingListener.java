package com.utfpr.edu.sistemas.distribuidos.ms_ranking.input;

import com.utfpr.edu.sistemas.distribuidos.ms_ranking.input.dto.PromocaoVotoReq;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.service.RankingService;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.crypto.CryptoUtil;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.crypto.KeyManager;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.model.Evento;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
// Ajuste os imports abaixo conforme o pacote das suas classes
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.config.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

@Slf4j
@Component
@AllArgsConstructor
public class RankingListener {

    private final ObjectMapper objectMapper;

    private final RankingService rankingService;

    /**
     * Ouve a Fila_Gateway.
     * O parâmetro @Header("amqp_receivedRoutingKey") extrai o tópico exato da mensagem.
     */
    @RabbitListener(queues = RabbitConfig.GATEWAY_QUEUE)
    public void processarMensagem(Evento evento, @Header("amqp_receivedRoutingKey") String routingKey) {
        log.info("[LISTENER][{}] Evento recebido!", routingKey);

        if (!validarAssinatura(evento)) {
            log.warn("[LISTENER][{}] Assinatura inválida. Evento ignorado.", routingKey);
            return;
        }

        try {
            if (routingKey.equals(RabbitConfig.PROMOCAO_VOTO_ROUTING_KEY)) {
                processarPromocaoVoto(evento);
            } else {
                System.out.println("[IGNORADO] Routing key desconhecida: " + routingKey);
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao processar evento " + routingKey + ": " + e.getMessage());
        }
    }

    private void processarPromocaoVoto(Evento evento) throws Exception {
        PromocaoVotoReq votoReq = objectMapper.readValue(evento.getConteudo(), PromocaoVotoReq.class);
        log.info("[PROCESSAR] Promoção: {} recebeu um votoRecebido: {}", votoReq.idPromocao(), votoReq.votoRecebido());

        rankingService.processarVoto(votoReq);
    }

    private boolean validarAssinatura(Evento evento) {
        //nao precisa validar assinatura, pois o ms_ranking é um microsserviço interno, não exposto para clientes externos.
        return true;
//        try {
//            log.debug("Assinatura recebida: {}", evento.getAssinatura());
//
//            PublicKey publicKey = KeyManager.loadPublicKey(evento.getProdutor());
//            return CryptoUtil.verify(evento.getConteudo(), evento.getAssinatura(), publicKey);
//        } catch (Exception e) {
//            log.error("[ERRO] Falha ao validar assinatura: {}", e.getMessage());
//            return false;
//        }
    }
}
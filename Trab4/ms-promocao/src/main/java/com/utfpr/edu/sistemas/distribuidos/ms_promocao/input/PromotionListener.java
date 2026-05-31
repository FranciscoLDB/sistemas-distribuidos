package com.utfpr.edu.sistemas.distribuidos.ms_promocao.input;

import com.utfpr.edu.sistemas.distribuidos.ms_promocao.input.dto.PromocaoCadReq;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.service.PromotionService;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.crypto.CryptoUtil;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.crypto.KeyManager;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Promocao;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
// Ajuste os imports abaixo conforme o pacote das suas classes
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.config.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

@Slf4j
@Component
@AllArgsConstructor
public class PromotionListener {

    private final ObjectMapper objectMapper;

    private final PromotionService promotionService;

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
            if (routingKey.equals(RabbitConfig.PROMOCAO_RECEBIDA_ROUTING_KEY)) {
                processarPromocaoRecebida(evento);
            } else {
                System.out.println("[IGNORADO] Routing key desconhecida: " + routingKey);
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao processar evento " + routingKey + ": " + e.getMessage());
        }
    }

    private void processarPromocaoRecebida(Evento evento) throws Exception {
        PromocaoCadReq promocaoReq = objectMapper.readValue(evento.getConteudo(), PromocaoCadReq.class);
        log.info("[PROCESSAR] Promoção recebida: {}", promocaoReq.nomeProduto());

        promotionService.cadastrarPromocao(promocaoReq);
    }

    private boolean validarAssinatura(Evento evento) {
        try {
            log.debug("Assinatura recebida: " + evento.getAssinatura());

            PublicKey publicKey = KeyManager.loadPublicKey(evento.getProdutor());
            return CryptoUtil.verify(evento.getConteudo(), evento.getAssinatura(), publicKey);
        } catch (Exception e) {
            log.error("[ERRO] Falha ao validar assinatura: {}", e.getMessage());
            return false;
        }
    }
}
package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Promocao;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.config.RabbitConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class PromotionListener {

    private final ObjectMapper objectMapper;

    /**
     * Ouve a Fila_Gateway.
     * O parâmetro @Header("amqp_receivedRoutingKey") extrai o tópico exato da mensagem.
     */
    @RabbitListener(queues = RabbitConfig.GATEWAY_QUEUE)
    public void processarMensagem(Evento evento, @Header("amqp_receivedRoutingKey") String routingKey) {
        log.info("[LISTENER][{}] Evento recebido!", routingKey);

        try {
            Promocao promocao = objectMapper.readValue(evento.getConteudo(), Promocao.class);

            // Roteamento interno da lógica baseado na Routing Key
            if (routingKey.equals("promocao.publicada")) {
                processarPromocaoPublicada(promocao);

            } else if (routingKey.equals("promocao.destaque")) {
                processarPromocaoDestaque(promocao);

            } else if (routingKey.startsWith("promocao.categoria.")) {
                String categoria = routingKey.replace("promocao.categoria.", "");
                processarPromocaoCategoria(promocao, categoria);

            } else {
                System.out.println("[IGNORADO] Routing key desconhecida: " + routingKey);
            }

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao processar evento " + routingKey + ": " + e.getMessage());
        }
    }

    private void processarPromocaoPublicada(Promocao promocao) {
        log.info("[nova_publicacao] {}", promocao.getNomeProduto());
        // Lógica: Salvar no repositório
//        PromotionController.notificarNovaPromocao(promocao);
    }

    private void processarPromocaoDestaque(Promocao promocao) {
        log.info("[destaque] Promoção em destaque: {}", promocao.getNomeProduto());
        // Lógica: Disparar SSE para clientes (Hot Deals)
        PromotionController.notificarPromocaoDestaque(promocao);
    }

    private void processarPromocaoCategoria(Promocao promocao, String categoriaTopico) {
        log.info("[categoria] Nova promoção na categoria '{}': {}", categoriaTopico, promocao.getNomeProduto());
        // Lógica: Buscar no banco quem segue essa categoria e disparar SSE
        PromotionController.notificarNovaPromocao(promocao);
    }

}
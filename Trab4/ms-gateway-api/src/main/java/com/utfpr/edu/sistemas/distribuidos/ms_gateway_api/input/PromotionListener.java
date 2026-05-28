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

        if (!validarAssinatura(evento)) {
            log.warn("[LISTENER][{}] Assinatura inválida. Evento ignorado.", routingKey);
            return;
        }

        try {
            // 2. Deserializar o conteúdo (JSON) para o objeto Promocao
            Promocao promocao = objectMapper.readValue(evento.getConteudo(), Promocao.class);

            // 3. Roteamento interno da lógica baseado na Routing Key
            if (routingKey.equals("promocao.publicada")) {
                processarPromocaoPublicada(promocao);

            } else if (routingKey.equals("promocao.destaque")) {
                processarPromocaoDestaque(promocao);

            } else if (routingKey.startsWith("promocao.categoria.")) {
                // Extrai a categoria exata do tópico (ex: "eletronicos" de "promocao.categoria.eletronicos")
                String categoria = routingKey.replace("promocao.categoria.", "");
                processarPromocaoCategoria(promocao, categoria);

            } else if (routingKey.equals("notificacao.hotdeal")) {
                processarNotificacaoHotdeal(promocao);

            } else {
                System.out.println("[IGNORADO] Routing key desconhecida: " + routingKey);
            }

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao processar evento " + routingKey + ": " + e.getMessage());
        }
    }

    // --- Métodos de Ação (Aqui você conectará com o SseService e Banco de Dados) ---

    private void processarPromocaoPublicada(Promocao promocao) {
        System.out.println("✅ [nova_publicacao] Salvando no banco: " + promocao.getNomeProduto());
        // Lógica: Salvar no repositório
    }

    private void processarPromocaoDestaque(Promocao promocao) {
        System.out.println("⭐ [destaque] Promoção em destaque: " + promocao.getNomeProduto());
        // Lógica: Disparar SSE para clientes (Hot Deals)
    }

    private void processarPromocaoCategoria(Promocao promocao, String categoriaTópico) {
        System.out.println("📦 [categoria] Nova promoção na categoria '" + categoriaTópico + "': " + promocao.getNomeProduto());
        // Lógica: Buscar no banco quem segue essa categoria e disparar SSE
    }

    private void processarNotificacaoHotdeal(Promocao promocao) {
        System.out.println("🔥 [hotdeal] Notificação geral de Hot Deal: " + promocao.getNomeProduto());
        // Lógica: Disparar notificação SSE para todos ou grupos específicos
    }

    // --- Lógica de Segurança Mockada (Substitua pela sua classe CryptoUtil) ---
    private boolean validarAssinatura(Evento evento) {
        // Implemente a chamada para CryptoUtil.verify(...) aqui
        return true;
    }
}
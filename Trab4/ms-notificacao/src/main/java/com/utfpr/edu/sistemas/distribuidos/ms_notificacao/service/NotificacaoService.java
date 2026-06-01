package com.utfpr.edu.sistemas.distribuidos.ms_notificacao.service;

import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Promocao;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@AllArgsConstructor
public class NotificacaoService {

    private static final String PRODUTOR_ID = "Ranking";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;
    private final EmailService emailService;

    public void processarPromocaoPublicada(Promocao promocao) throws Exception {
        String emailLoja = promocao.getLoja().getEmail();
        String mensagem = gerarMensagemEmail(promocao);

        emailService.enviarEmail(emailLoja, "Promoção publicada!", mensagem);

        String categoriaRoutingKey = "promocao.categoria." + promocao.getCategoria().toLowerCase().replace(" ", "");
        Evento evento = geraEvento(promocao, categoriaRoutingKey);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, categoriaRoutingKey, evento);
        log.info("[NOTIFICACAO][PUBLICADA] Evento de publicação publicado no RabbitMQ: {}", evento.getTipo());
    }

    public void processarPromocaoDestaque(Promocao promocao) throws Exception {
        String emailLoja = promocao.getLoja().getEmail();
        String mensagem = gerarMensagemDestaque(promocao);

        emailService.enviarEmail(emailLoja, "Promoção HOT DEAL!", mensagem);

        String categoriaRoutingKey = "promocao.categoria." + promocao.getCategoria().toLowerCase().replace(" ", "");
        Evento evento = geraEvento(promocao, categoriaRoutingKey);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, categoriaRoutingKey, evento);
        log.info("[NOTIFICACAO][DESTAQUE] Evento de destaque publicado no RabbitMQ: {}", evento.getTipo());
    }

    private String gerarMensagemEmail(Promocao promocao) {
        return String.format(
                "<html><body style='font-family: sans-serif; line-height: 1.6; color: #333;'>" +
                        "<h2>🚀 Boas notícias, %s!</h2>" +
                        "<p>Sua nova promoção foi publicada e já está disponível para a comunidade.</p>" +
                        "<div style='background-color: #f8f9fa; padding: 15px; border-radius: 8px; border: 1px solid #ddd;'>" +
                        "   <h3 style='margin-top: 0;'>--- Detalhes do Anúncio ---</h3>" +
                        "   <p><strong>📦 Produto:</strong> %s</p>" +
                        "   <p><strong>📝 Descrição:</strong> %s</p>" +
                        "   <p><strong>💰 Valor:</strong> <span style='color: #28a745; font-weight: bold;'>R$ %.2f</span></p>" +
                        "   <p><strong>🏷️ Categoria:</strong> %s</p>" +
                        "</div>" +
                        "<p>O status atual é: <strong>%s</strong>.</p>" +
                        "<p>Assim que atingir 5 votos positivos, ela receberá o selo de <b>DESTAQUE! ⭐</b></p>" +
                        "<br>" +
                        "<p>Boas vendas,<br><strong>Equipe de Promoções UTFPR</strong></p>" +
                        "</body></html>",
                promocao.getLoja().getNome(),
                promocao.getNomeProduto(),
                promocao.getDescricao(),
                promocao.getPrecoPromocional(),
                promocao.getCategoria(),
                promocao.getStatus()
        );
    }

    private String gerarMensagemDestaque(Promocao promocao) {
        return String.format(
                "<html><body style='font-family: sans-serif; line-height: 1.6; color: #333;'>" +
                        "<h2 style='color: #d4af37;'>⭐ PARABÉNS, %s! ⭐</h2>" +
                        "<h3>SUA PROMOÇÃO EXPLODIU!</h3>" +
                        "<p>Temos o prazer de informar que o seu produto <strong>'%s'</strong> acaba de atingir a marca de <strong>%d votos</strong>!</p>" +
                        "<div style='background-color: #fff3cd; padding: 15px; border-radius: 8px; border: 1px solid #ffeeba;'>" +
                        "   <h4 style='margin-top: 0; color: #856404;'>🚀 O que acontece agora?</h4>" +
                        "   <p>Sua promoção subiu para o status de <strong>DESTAQUE</strong>. Ela agora tem prioridade visual e aparecerá no topo das buscas para todos os usuários.</p>" +
                        "</div>" +
                        "<br>" +
                        "<p><strong>🔥 Engajamento atual:</strong> %d votos da comunidade</p>" +
                        "<p>Continue assim! Boas ofertas sempre chegam ao topo.</p>" +
                        "<br>" +
                        "<p>Atenciosamente,<br><strong>Equipe de Curadoria UTFPR</strong></p>" +
                        "</body></html>",
                promocao.getLoja().getNome(),
                promocao.getNomeProduto(),
                promocao.getVotos(),
                promocao.getVotos()
        );
    }

    private Evento geraEvento(Object request, String routingKey) throws Exception {
        String promotionJson = objectMapper.writeValueAsString(request);
        return new Evento(routingKey, promotionJson, PRODUTOR_ID);
    }
}

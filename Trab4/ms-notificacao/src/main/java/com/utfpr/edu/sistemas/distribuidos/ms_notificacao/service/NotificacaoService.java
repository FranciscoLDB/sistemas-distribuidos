package com.utfpr.edu.sistemas.distribuidos.ms_notificacao.service;

import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.input.dto.PromocaoVotoReq;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.repository.LojaRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.repository.PromotionRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.crypto.CryptoUtil;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.crypto.KeyManager;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Loja;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Promocao;
import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Status;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class NotificacaoService {

    private static final String PRODUTOR_ID = "Ranking";
    private final Integer DESTAQUE_TRIGGER = 5;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;
    private final PromotionRepository promotionRepository;
    private final LojaRepository lojaRepository;
    private final EmailService emailService;

    public void processarPromocaoPublicada(Promocao promocao) throws Exception {
        String emailLoja = promocao.getLoja().getEmail();
        String mensagem = gerarMensagemEmail(promocao);

        emailService.enviarEmail(emailLoja, "Promoção publicada!", mensagem);

        String categoriaRoutingKey = "promocao." + promocao.getCategoria().toLowerCase().replace(" ", "");
        Evento evento = geraEvento(promocao, categoriaRoutingKey);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, categoriaRoutingKey, evento);
        log.info("[NOTIFICACAO][PUBLICADA] Evento de publicação publicado no RabbitMQ: {}", evento.getTipo());
    }

    public void processarPromocaoDestaque(Promocao promocao) throws Exception {
        String emailLoja = promocao.getLoja().getEmail();
        String mensagem = gerarMensagemEmail(promocao);

        emailService.enviarEmail(emailLoja, "Promoção HOT DEAL!", mensagem);

        String categoriaRoutingKey = "promocao." + promocao.getCategoria().toLowerCase().replace(" ", "");
        Evento evento = geraEvento(promocao, categoriaRoutingKey);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, categoriaRoutingKey, evento);
        log.info("[NOTIFICACAO][DESTAQUE] Evento de destaque publicado no RabbitMQ: {}", evento.getTipo());
    }

    private String gerarMensagemEmail(Promocao promocao) {
        return String.format("Olá %s, sua promoção '%s' foi publicada com sucesso!", promocao.getLoja().getNome(), promocao.getNomeProduto());
    }

    private Evento geraEvento(Object request, String routingKey) throws Exception {
        String promotionJson = objectMapper.writeValueAsString(request);
        return new Evento(RabbitConfig.PROMOCAO_DESTAQUE_ROUTING_KEY, promotionJson, PRODUTOR_ID);
    }
}

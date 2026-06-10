package com.utfpr.edu.sistemas.distribuidos.ms_ranking.service;

import com.utfpr.edu.sistemas.distribuidos.ms_ranking.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.input.dto.PromocaoVotoReq;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.repository.PromotionRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.crypto.CryptoUtil;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.crypto.KeyManager;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.model.Promocao;
import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.model.Status;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class RankingService {

    private static final String PRODUTOR_ID = "Ranking";
    private final Integer DESTAQUE_TRIGGER = 5;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;
    private final PromotionRepository promotionRepository;

    public void processarVoto(PromocaoVotoReq request) throws Exception {
        Promocao promocao = atualizaBase(request);
        log.info("[PROMOCAO][VOTO][PROMOCAO: {}] Voto registrado no banco de dados!", request.idPromocao());

        if(promocaoVirouDestaque(promocao)) {
            log.info("[PROMOCAO][VOTO][PROMOCAO: {}] Promoção {} atingiu o destaque com {} votos!", request.idPromocao(), promocao.getId(), promocao.getVotos());
            publicarEventoDestaque(promocao);
        }
    }

    private void publicarEventoDestaque(Promocao promocao) throws Exception {
        Evento evento = geraEvento(promocao);

        // Assina mensagem com chave privada
        evento.setAssinatura(
                CryptoUtil.sign(evento.getConteudo(), KeyManager.loadPrivateKey(PRODUTOR_ID))
        );
        log.info("[PROMOCAO][VOTO] Evento gerado e assinado: {}", evento.getTipo());

        // Publica mensagem no topico do RabbitMQ promocao.destaque
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.PROMOCAO_DESTAQUE_ROUTING_KEY, evento);
        log.info("[PROMOCAO][VOTO] Evento publicado no RabbitMQ: {}", evento.getTipo());
    }

    private boolean promocaoVirouDestaque(Promocao promocao) {
        return Objects.equals(promocao.getVotos(), DESTAQUE_TRIGGER);
    }

    private Promocao atualizaBase(PromocaoVotoReq request) {
        Promocao promocao = promotionRepository.findById(request.idPromocao())
                .orElseThrow(() -> new RuntimeException("Promoção não encontrada: " + request.idPromocao()));

        promocao.setVotos(promocao.getVotos() + request.votoRecebido());
        if (promocao.getVotos() >= DESTAQUE_TRIGGER && promocao.getStatus() != Status.DESTAQUE) {
            promocao.setStatus(Status.DESTAQUE);
        }

        promotionRepository.save(promocao);
        return promocao;
    }

    private Evento geraEvento(Object request) throws Exception {
        String promotionJson = objectMapper.writeValueAsString(request);
        return new Evento(RabbitConfig.PROMOCAO_DESTAQUE_ROUTING_KEY, promotionJson, PRODUTOR_ID);
    }
}

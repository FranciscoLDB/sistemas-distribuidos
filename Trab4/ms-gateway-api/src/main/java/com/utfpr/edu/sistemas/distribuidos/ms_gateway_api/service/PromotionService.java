package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoCadReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoVotoReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.PromotionRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Promocao;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PromotionService {

    private static final String PRODUTOR_ID = "Gateway";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;
    private final PromotionRepository promotionRepository;

    public Object cadastrarPromocao(PromocaoCadReq request, String assinatura, String requisitor) throws Exception {
        // Converte requesicao para mensagem de evento
        String promotionJson = objectMapper.writeValueAsString(request);
        Evento evento = new Evento(RabbitConfig.PROMOCAO_RECEBIDA_ROUTING_KEY, promotionJson, requisitor);
        evento.setAssinatura(assinatura);

        // Publica mensagem no topico do RabbitMQ promocao.recebida
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.PROMOCAO_RECEBIDA_ROUTING_KEY, evento);
        log.info("[PROMOCAO][CADASTRAR] Evento publicado no RabbitMQ: {}", evento);
        return "Promoção enviada para cadastro!";
    }

    public List<Promocao> listarPromocoes() {
        Iterable<Promocao> promocoesOpt = promotionRepository.findAll();
        return (List<Promocao>) promocoesOpt;
    }

    public Object votarPromocao(PromocaoVotoReq request, String assinatura, String requisitor) throws Exception {
        // Converte requesicao para mensagem de evento
        String votoJson = objectMapper.writeValueAsString(request);
        Evento evento = new Evento(RabbitConfig.PROMOCAO_VOTO_ROUTING_KEY, votoJson, requisitor);
        evento.setAssinatura(assinatura);

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.PROMOCAO_VOTO_ROUTING_KEY, evento);
        log.info("[PROMOCAO][VOTAR] Evento publicado no RabbitMQ: {}", evento);
        return "Voto registrado com sucesso!";
    }
}

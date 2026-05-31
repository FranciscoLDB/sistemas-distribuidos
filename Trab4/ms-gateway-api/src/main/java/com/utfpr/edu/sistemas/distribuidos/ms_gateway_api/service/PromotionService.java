package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.dto.PromocaoCadReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Evento;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@AllArgsConstructor
public class PromotionService {

    private static final String PRODUTOR_ID = "Gateway";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;

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

    public String listarPromocoes() {
        return "Lista de promoções retornada com sucesso!";
    }

    public String votarPromocao() {
        return "Voto registrado com sucesso!";
    }

    public String cadastrarInteresse() {
        return "Interesse cadastrado com sucesso!";
    }

    public String removerInteresse() {
        return "Interesse removido com sucesso!";
    }
}

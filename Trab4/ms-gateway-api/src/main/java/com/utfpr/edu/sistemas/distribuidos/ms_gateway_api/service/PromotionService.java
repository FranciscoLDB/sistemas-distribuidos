package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoCadReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoVotoReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.PromotionRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.UsuarioRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.CategoriaRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Categoria;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Promocao;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class PromotionService {

    private static final String PRODUTOR_ID = "Gateway";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;
    private final PromotionRepository promotionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;

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
        log.info("[PROMOCAO][VOTAR] Processando voto para promoção ID: {}, voto recebido: {}", request.idPromocao(), request.votoRecebido());
        // Converte requesicao para mensagem de evento
        String votoJson = objectMapper.writeValueAsString(request);
        Evento evento = new Evento(RabbitConfig.PROMOCAO_VOTO_ROUTING_KEY, votoJson, requisitor);
        evento.setAssinatura(assinatura);

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.PROMOCAO_VOTO_ROUTING_KEY, evento);
        log.info("[PROMOCAO][VOTAR] Evento publicado no RabbitMQ: {}", evento);
        return Map.of("mensagem", "Voto registrado com sucesso!");
    }

    public List<String> buscarInteressesConsumidor(String consumidorId) {
        // Lógica para buscar interesses do consumidor (exemplo: consulta ao banco de dados)
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(Long.parseLong(consumidorId));
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            log.info("[INTERESSES][BUSCAR] Interesses do consumidor {}: {}", consumidorId, usuario.getCategorias());
            return usuario.getCategorias().stream()
                    .map(Categoria::getNome)
                    .toList();
        }
        return List.of();
    }

    public List<Categoria> listarCategorias() {
        Iterable<Categoria> all = categoriaRepository.findAll();
        List<Categoria> result = new ArrayList<>();
        all.forEach(result::add);
        return result;
    }
}

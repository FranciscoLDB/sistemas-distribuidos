package com.utfpr.edu.sistemas.distribuidos.ms_promocao.service;

import com.utfpr.edu.sistemas.distribuidos.ms_promocao.config.RabbitConfig;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.input.dto.PromocaoCadReq;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.repository.LojaRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.repository.PromotionRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.crypto.CryptoUtil;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.crypto.KeyManager;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Evento;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Loja;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Promocao;
import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Status;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@AllArgsConstructor
public class PromotionService {

    private static final String PRODUTOR_ID = "Promocao";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate;
    private final PromotionRepository promotionRepository;
    private final LojaRepository lojaRepository;

    public void cadastrarPromocao(PromocaoCadReq request) throws Exception {
        Loja loja = lojaRepository.findById(request.lojaId())
                .orElseThrow(() -> new RuntimeException("Loja não encontrada com id: " + request.lojaId()));

        Promocao promocao = converterParaPromocao(request);
        promocao.setLoja(loja);
        var p = promotionRepository.save(promocao);
        log.info("[PROMOCAO][CADASTRAR] Promoção salva no banco de dados: {}", promocao.getId());

        Evento evento = geraEvento(p);

        // Assina mensagem com chave privada
        evento.setAssinatura(
                CryptoUtil.sign(evento.getConteudo(), KeyManager.loadPrivateKey(PRODUTOR_ID))
        );
        log.info("[PROMOCAO][CADASTRAR] Evento gerado e assinado: {}", evento.getTipo());

        // Publica mensagem no topico do RabbitMQ promocao.publicada
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.PROMOCAO_PUBLICADA_ROUTING_KEY, evento);
        log.info("[PROMOCAO][CADASTRAR] Evento publicado no RabbitMQ: {}", evento.getTipo());
    }

    private Promocao converterParaPromocao(PromocaoCadReq request) {
        Promocao promocao = new Promocao();
        promocao.setCategoria(request.categoria());
        promocao.setNomeProduto(request.nomeProduto());
        promocao.setDescricao(request.descricao());
        promocao.setPrecoOriginal(request.precoOriginal());
        promocao.setPrecoPromocional(request.precoPromocional());
        promocao.setVotos(0);
        promocao.setStatus(Status.NORMAL);
        return promocao;
    }

    private Evento geraEvento(Object request) throws Exception {
        String promotionJson = objectMapper.writeValueAsString(request);
        return new Evento(RabbitConfig.PROMOCAO_PUBLICADA_ROUTING_KEY, promotionJson, PRODUTOR_ID);
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

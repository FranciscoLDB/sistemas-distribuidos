package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoInteresseReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoVotoReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service.PromotionService;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.PromocaoCadReq;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Promocao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Categoria;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Permite requisições de qualquer origem (CORS)
public class PromotionController {

    private final PromotionService promotionService;

    private static final Map<String, SseEmitter> emissoresSse = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> interessesConsumidores = new ConcurrentHashMap<>();

    @PostMapping("/promocao")
    public ResponseEntity<?> cadastrarPromocao(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody PromocaoCadReq request) throws Exception {
        log.info("[API][PROMOCAO][CADASTRAR] Endpoint de cadastro de promoção acessado.");
        return ResponseEntity.ok(promotionService.cadastrarPromocao(request, assinatura, requisitor));
    }

    @GetMapping("/promocao")
    public ResponseEntity<?> listarPromocoes() {
        log.info("[API][PROMOCOES][LISTAR] Endpoint de listagem de promoções acessado.");
        return ResponseEntity.ok(promotionService.listarPromocoes());
    }

    @PostMapping("/promocao/votar")
    public ResponseEntity<?> votarPromocao(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody PromocaoVotoReq request) throws Exception {
        log.info("[API][PROMOCAO][VOTAR] Endpoint de votação de promoção acessado.");
        return ResponseEntity.accepted().body(promotionService.votarPromocao(request, assinatura, requisitor));
    }

    @GetMapping(value = "/promocao/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter inscreverStreamNotificacoes(
            @RequestHeader(value = "X-Requisitor", required = false) String headerConsumidorId,
            @RequestParam(value = "consumidorId", required = false) String queryConsumidorId) {

        // Escolhe quem estiver preenchido (se veio do curl ou do Angular)
        String consumidorId = (queryConsumidorId != null) ? queryConsumidorId : headerConsumidorId;

        if (consumidorId == null || consumidorId.isBlank()) {
            log.warn("[SSE] Tentativa de conexão sem identificação de consumidor.");
            // Retorna um emitter completado imediatamente para evitar vazamento
            SseEmitter erroEmitter = new SseEmitter();
            erroEmitter.complete();
            return erroEmitter;
        }

        log.info("[API][SSE][INSCREVER] Cliente {} conectado com sucesso.", consumidorId);

        SseEmitter emitter = new SseEmitter(3600_000L);
        emissoresSse.put(consumidorId, emitter);
        // Busca interesses do consumidor e mantém em memória
        buscarInteressesConsumidor(consumidorId);

        // Envia lista de categorias disponíveis e interesses do usuário assim que conectar
        try {
            List<Categoria> categorias = promotionService.listarCategorias();
            emitter.send(SseEmitter.event()
                    .name("CATEGORIAS")
                    .data(categorias, MediaType.APPLICATION_JSON));

            List<String> interesses = interessesConsumidores.getOrDefault(consumidorId, List.of());
            emitter.send(SseEmitter.event()
                    .name("INTERESSES")
                    .data(interesses, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.error("[SSE] Falha ao enviar listas iniciais para {}: {}", consumidorId, e.getMessage());
            emitter.complete();
            emissoresSse.remove(consumidorId);
            interessesConsumidores.remove(consumidorId);
            return emitter;
        }

        // Remove do mapa quando a conexão terminar com sucesso ou der timeout
        emitter.onCompletion(() -> {
            log.info("[SSE] Conexão completada para o cliente {}.", consumidorId);
            emissoresSse.remove(consumidorId);
            interessesConsumidores.remove(consumidorId);
        });

        emitter.onTimeout(() -> {
            log.warn("[SSE] Timeout de conexão atingido para o cliente {}.", consumidorId);
            emitter.complete();
            emissoresSse.remove(consumidorId);
            interessesConsumidores.remove(consumidorId);
        });

        emitter.onError((ex) -> {
            log.error("[SSE] Erro na conexão do cliente {}: {}", consumidorId, ex.getMessage());
            emitter.complete();
            emissoresSse.remove(consumidorId);
            interessesConsumidores.remove(consumidorId);
        });

        return emitter;
    }

    public static void notificarNovaPromocao(Promocao dadosPromocao) {
        log.info("[API][SSE][NOTIFICAR] Disparando nova promoção em tempo real para todos os clientes.");

        // Percorre todas as conexões ativas enviando o evento
        emissoresSse.forEach((consumidorId, emitter) -> {
            if (interessesConsumidores.get(consumidorId).contains(dadosPromocao.getCategoria().getNome())) {
                notificaUsuario(dadosPromocao, consumidorId, emitter, "NOVA_PROMOCAO");
            }
        });
    }

    public static void notificarPromocaoDestaque(Promocao dadosPromocao) {
        log.info("[API][SSE][NOTIFICAR] Disparando promoção em destaque para todos os clientes.");

        // Percorre todas as conexões ativas enviando o evento
        emissoresSse.forEach((consumidorId, emitter) -> {
            notificaUsuario(dadosPromocao, consumidorId, emitter, "PROMOCAO_DESTAQUE");
        });
    }

    private void buscarInteressesConsumidor(String consumidorId) {
        List<String> interesses = promotionService.buscarInteressesConsumidor(consumidorId);
        interessesConsumidores.put(consumidorId, interesses);
        log.info("[API][SSE][INTERESSES] Interesses do consumidor {}: {}", consumidorId, interesses);
    }

    private static void notificaUsuario(Promocao dadosPromocao, String consumidorId, SseEmitter emitter, String eventName) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(String.valueOf(System.currentTimeMillis()))
                    .data(dadosPromocao, MediaType.APPLICATION_JSON));
            log.info("[API][SSE][NOTIFICAR] Promoção '{}' enviada para consumidor {}.", dadosPromocao.getNomeProduto(), consumidorId);
        } catch (IOException e) {
            log.warn("[SSE] Falha ao enviar notificação para {}, removendo conexão quebrada.", consumidorId);
            emitter.complete();
            emissoresSse.remove(consumidorId);
        }
    }

    public static void atualizarInteressesConsumidor(String consumidorId, List<String> novosInteresses) {
        interessesConsumidores.put(consumidorId, novosInteresses);
        log.info("[API][SSE][INTERESSES] Interesses do consumidor {} atualizados: {}", consumidorId, novosInteresses);
    }

}

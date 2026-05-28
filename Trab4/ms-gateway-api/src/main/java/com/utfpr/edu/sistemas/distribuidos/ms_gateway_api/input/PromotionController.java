package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service.PromotionService;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.dto.PromocaoCadReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping("/promocao")
    public ResponseEntity<?> cadastrarPromocao(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody PromocaoCadReq request) throws Exception {
        log.info("[API][PROMOCAO][CADASTRAR] Endpoint de cadastro de promoção acessado.");
        return ResponseEntity.ok(promotionService.cadastrarPromocao(request, assinatura, requisitor));
    }

    @GetMapping("/promocao")
    public String listarPromocoes() {
        log.info("[API][PROMOCOES][LISTAR] Endpoint de listagem de promoções acessado.");
        return promotionService.listarPromocoes();
    }

    @PostMapping("/promocao/votar")
    public String votarPromocao() {
        log.info("[API][PROMOCAO][VOTAR] Endpoint de votação de promoção acessado.");
        return promotionService.votarPromocao();
    }

    @PostMapping("/promocao/interesse")
    public String cadastrarInteresse() {
        log.info("[API][PROMOCAO][INTERESSE][CADASTRAR] Endpoint de cadastro de interesse acessado.");
        return promotionService.cadastrarInteresse();
    }

    @DeleteMapping("/promocao/interesse")
    public String removerInteresse() {
        log.info("[API][PROMOCAO][INTERESSE][REMOVER] Endpoint de remoção de interesse acessado.");
        return promotionService.removerInteresse();
    }
}

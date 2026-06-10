package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.CreateLojaRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.UserInteresseRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.CreateUserRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service.RegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RegisterController {

    private final RegisterService registerService;

    @PostMapping("/user")
    public ResponseEntity<?> cadastrarUsuario(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody CreateUserRequest request) throws Exception {
        log.info("[API][USUARIO][CADASTRAR] Endpoint de cadastro de usuário acessado.");
        return ResponseEntity.ok(registerService.cadastrarUsuario(request, assinatura, requisitor));
    }

    @PostMapping("/loja")
    public ResponseEntity<?> cadastrarLoja(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody CreateLojaRequest request) throws Exception {
        log.info("[API][LOJA][CADASTRAR] Endpoint de cadastro de loja acessado.");
        return ResponseEntity.ok(registerService.cadastrarLoja(request, assinatura, requisitor));
    }

    @PostMapping("/user/interesse")
    public ResponseEntity<List<Long>> cadastrarInteresse(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody UserInteresseRequest request) throws Exception {
        log.info("[API][USUARIO][INTERESSE][CADASTRAR] Endpoint de cadastro de interesse do usuário acessado.");

        List<Long> interessesAtualizados = registerService.cadastrarInteresseUsuario(request, assinatura, requisitor);
        return ResponseEntity.ok(interessesAtualizados);
    }

    @DeleteMapping("/user/interesse")
    public ResponseEntity<List<Long>> removerInteresse(
            @RequestHeader("X-Requisitor") String requisitor,
            @RequestHeader("X-Assinatura") String assinatura,
            @RequestBody UserInteresseRequest request) throws Exception {
        log.info("[API][USUARIO][INTERESSE][REMOVER] Endpoint de remoção de interesse do usuário acessado.");
        List<Long> interessesAtualizados = registerService.removerInteresseUsuario(request, assinatura, requisitor);
        return ResponseEntity.ok(interessesAtualizados);
    }
}

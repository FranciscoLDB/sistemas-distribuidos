package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.CreateUserRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service.RegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
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
}

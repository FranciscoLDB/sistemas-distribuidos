package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto;

import java.util.List;

public record CreateUserRequest(
        String nome,
        String senha,
        List<Long> categoriasInteresse
) {
}

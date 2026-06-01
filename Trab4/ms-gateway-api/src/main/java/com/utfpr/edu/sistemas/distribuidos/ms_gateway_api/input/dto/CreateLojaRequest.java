package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto;

public record CreateLojaRequest(
        String nome,
        String email,
        String imagemUrl
) {
}

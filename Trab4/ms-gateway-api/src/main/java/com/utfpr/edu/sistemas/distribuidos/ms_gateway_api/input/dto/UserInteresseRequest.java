package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto;

import java.util.List;

public record UserInteresseRequest(
        Long usuarioId,
        List<Long> categoriasInteresse
) {
}

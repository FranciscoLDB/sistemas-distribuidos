package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto;

import java.math.BigDecimal;

public record PromocaoCadReq(
        String nomeProduto,
        String descricao,
        BigDecimal precoOriginal,
        BigDecimal precoPromocional,
        String categoria,
        Long lojaId
) {
}

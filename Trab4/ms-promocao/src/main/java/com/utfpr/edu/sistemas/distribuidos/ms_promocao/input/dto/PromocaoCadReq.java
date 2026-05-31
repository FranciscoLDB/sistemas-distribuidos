package com.utfpr.edu.sistemas.distribuidos.ms_promocao.input.dto;

import java.math.BigDecimal;

public record PromocaoCadReq(
        String nomeProduto,
        String descricao,
        BigDecimal valorOriginal,
        BigDecimal valorPromocional,
        String categoria,
        String email
) {
}

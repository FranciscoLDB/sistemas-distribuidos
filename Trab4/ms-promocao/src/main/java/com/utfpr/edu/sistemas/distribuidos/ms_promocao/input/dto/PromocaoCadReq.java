package com.utfpr.edu.sistemas.distribuidos.ms_promocao.input.dto;

import java.math.BigDecimal;

public record PromocaoCadReq(
        String nomeProduto,
        String descricao,
        BigDecimal precoOriginal,
        BigDecimal precoPromocional,
        Long categoriaId,
        Long lojaId
) {
}

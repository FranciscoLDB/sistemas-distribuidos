package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.dto;

import java.math.BigInteger;

public record PromocaoCadReq(
    String nome,
    String descricao,
    BigInteger valorOriginal,
    BigInteger valorPromocional,
    String categoria,
    String email
) {
}

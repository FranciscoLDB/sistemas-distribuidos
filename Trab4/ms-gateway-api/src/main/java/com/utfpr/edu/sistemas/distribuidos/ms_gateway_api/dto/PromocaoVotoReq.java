package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.dto;

public record PromocaoVotoReq(
    String promocaoId,
    String userId,
    Integer voto
) {
}

package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Promocao {

    @JsonProperty("id")
    private String id;

    @JsonProperty("nomeProduto")
    private String nomeProduto;

    @JsonProperty("precoOriginal")
    private double precoOriginal;

    @JsonProperty("precoPromocional")
    private double precoPromocional;

    @JsonProperty("categoria")
    private String categoria;

    @JsonProperty("votos")
    private int votos;

    // yyyy-MM-dd HH:mm:ss
    @JsonProperty("dataCriacao")
    private String dataCriacao;

    @JsonProperty("status")
    private String status; // NORMAL, DESTAQUE

    // Constructor for backward compatibility
    public Promocao(String id, String nomeProduto, double preco) {
        this.id = id;
        this.nomeProduto = nomeProduto;
        this.precoOriginal = preco;
        this.precoPromocional = preco;
        this.votos = 0;
        this.dataCriacao = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.status = "NORMAL";
        this.categoria = "Geral";
    }

    @Override
    public String toString() {
        return String.format("Promoção: %s | R$ %.2f → R$ %.2f | Votos: %d",
                nomeProduto, precoOriginal, precoPromocional,  votos);
    }
}
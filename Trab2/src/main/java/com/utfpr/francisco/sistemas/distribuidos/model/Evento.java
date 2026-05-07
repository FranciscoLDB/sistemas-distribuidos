package com.utfpr.francisco.sistemas.distribuidos.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Classe genérica para representar eventos assinados
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Evento {

    @JsonProperty("tipo")
    private String tipo; // ex: "promocao.recebida", "promocao.voto"

    @JsonProperty("conteudo")
    private String conteudo; // JSON do objeto original

    @JsonProperty("assinatura")
    private String assinatura; // Assinatura digital em Base64

    @JsonProperty("produtor")
    private String produtor; // Identificador do microsserviço produtor

    public Evento(String tipo, String conteudo, String produtor) {
        this.tipo = tipo;
        this.conteudo = conteudo;
        this.produtor = produtor;
        this.assinatura = null;
    }
}

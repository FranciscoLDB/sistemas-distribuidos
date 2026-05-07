package com.utfpr.francisco.sistemas.distribuidos.model;

    import com.fasterxml.jackson.annotation.JsonProperty;
    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.Id;
    import jakarta.persistence.Table;
    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;

    import java.time.LocalDateTime;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Entity
    @Table(name = "promocoes")
    public class Promocao {

        @Id
        @Column(name = "id")
        @JsonProperty("id")
        private String id;

        @Column(name = "nome_produto")
        @JsonProperty("nomeProduto")
        private String nomeProduto;

        @Column(name = "preco_original")
        @JsonProperty("precoOriginal")
        private double precoOriginal;

        @Column(name = "preco_promocional")
        @JsonProperty("precoPromocional")
        private double precoPromocional;

        @Column(name = "categoria")
        @JsonProperty("categoria")
        private String categoria;

        @Column(name = "votos")
        @JsonProperty("votos")
        private int votos;

        // yyyy-MM-dd HH:mm:ss
        @Column(name = "data_criacao")
        @JsonProperty("dataCriacao")
        private String dataCriacao;

        @Column(name = "status")
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
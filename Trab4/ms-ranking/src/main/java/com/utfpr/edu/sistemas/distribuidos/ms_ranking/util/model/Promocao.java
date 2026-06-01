package com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "promocoes")
public class Promocao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String nomeProduto;

    private String descricao;

    private BigDecimal precoOriginal;

    private BigDecimal precoPromocional;

    @ManyToOne
    private Categoria categoria;

    private Integer votos;

    @Column(
            name = "data_criacao",
            nullable = false,
            updatable = false,
            insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
    )
    private LocalDateTime dataCriacao;

    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne
    private Loja loja;

    @Override
    public String toString() {
        return String.format("Promoção: %s | R$ %.2f → R$ %.2f | Votos: %d",
                nomeProduto, precoOriginal, precoPromocional,  votos);
    }
}
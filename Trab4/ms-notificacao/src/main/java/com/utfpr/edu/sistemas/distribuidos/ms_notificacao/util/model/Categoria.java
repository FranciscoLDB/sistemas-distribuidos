package com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String nome;

     @Override
    public String toString() {
        return String.format("Categoria: %s", nome);
    }
}

package com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lojas")
public class Loja {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String nome;

    private String email;

    private String imagemUrl;

    @Override
    public String toString() {
        return String.format("Loja: %s | Email: %s | Imagem: %s", nome, email, imagemUrl);
    }

}

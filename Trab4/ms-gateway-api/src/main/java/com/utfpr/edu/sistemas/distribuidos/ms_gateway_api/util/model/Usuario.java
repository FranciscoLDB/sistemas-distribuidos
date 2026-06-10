package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String nome;
    private String senha;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Categoria> categorias = new ArrayList<>();

    public void adicionarInteresse(Categoria categoria) {
        if(!categorias.contains(categoria)) {
            categorias.add(categoria);
        }
    }

    @Override
    public String toString() {
        return String.format("Usuário: %s | Senha: %s", nome, senha);
    }
}
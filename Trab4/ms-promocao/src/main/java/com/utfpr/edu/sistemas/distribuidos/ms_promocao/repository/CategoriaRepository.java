package com.utfpr.edu.sistemas.distribuidos.ms_promocao.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Categoria;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaRepository extends CrudRepository<Categoria, Long> {
}

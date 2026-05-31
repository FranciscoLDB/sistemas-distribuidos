package com.utfpr.edu.sistemas.distribuidos.ms_notificacao.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_notificacao.util.model.Loja;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LojaRepository extends CrudRepository<Loja, Long> {
}

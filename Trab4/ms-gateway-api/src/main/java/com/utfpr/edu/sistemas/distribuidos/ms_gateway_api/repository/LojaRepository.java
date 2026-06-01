package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Loja;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LojaRepository extends CrudRepository<Loja, Long> {
}

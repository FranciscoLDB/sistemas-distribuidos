package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Promocao;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends CrudRepository<Promocao, Long> {
}

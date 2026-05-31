package com.utfpr.edu.sistemas.distribuidos.ms_promocao.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_promocao.util.model.Promocao;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends CrudRepository<Promocao, Long> {
}

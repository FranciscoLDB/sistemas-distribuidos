package com.utfpr.edu.sistemas.distribuidos.ms_ranking.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_ranking.util.model.Promocao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PromotionRepository extends CrudRepository<Promocao, Long> {

    @Modifying
    @Transactional(propagation = Propagation.REQUIRED)
    @Query("UPDATE Promocao p " +
            "SET p.votos = p.votos + :valorVoto, " +
            "p.status = CASE " +
            "  WHEN (p.votos + :valorVoto) >= 5 THEN 'DESTAQUE' " +
            "  ELSE 'NORMAL' " +
            "END " +
            "WHERE p.id = :id")
    void registrarVoto(Long id, Integer valorVoto);
}

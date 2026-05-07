package com.utfpr.francisco.sistemas.distribuidos.database;

import com.utfpr.francisco.sistemas.distribuidos.model.Promocao;
import jakarta.persistence.*;

import java.util.List;

/**
 * Repositório JPA para armazenar promoções publicadas no H2
 */
public class PromocaoRepository {

    private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("promocoesPU");
    private static EntityManager em = emf.createEntityManager();

    /**
     * Salvar uma promoção publicada
     * @param promocao a promoção a ser salva
     */
    public void salvar(Promocao promocao) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            promocao.setStatus("PUBLICADA");
            em.merge(promocao); // merge para insert/update
            tx.commit();
            System.out.println("✓ Promoção salva no banco de dados: " + promocao.getNomeProduto());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
    }

    /**
     * Obter todas as promoções publicadas
     * @return lista de promoções publicadas
     */
    public List<Promocao> obterTodas() {
        TypedQuery<Promocao> query = em.createQuery("SELECT p FROM Promocao p WHERE p.status = 'PUBLICADA'", Promocao.class);
        return query.getResultList();
    }

    /**
     * Obter uma promoção por ID
     * @param id o ID da promoção
     * @return a promoção ou null se não encontrada
     */
    public Promocao obterPorId(String id) {
        return em.find(Promocao.class, id);
    }

    /**
     * Incrementar votos de uma promoção (não usado diretamente, pois votos são via evento)
     * @param id o ID da promoção
     * @return true se conseguiu votar, false caso contrário
     */
    public boolean votar(String id) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Promocao promocao = em.find(Promocao.class, id);
            if (promocao != null) {
                promocao.setVotos(promocao.getVotos() + 1);
                em.merge(promocao);
                tx.commit();
                return true;
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
        return false;
    }

    public void votar(Promocao promocao, int votos) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            promocao.setVotos(votos);
            em.merge(promocao);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
    }

    /**
     * Limpar todas as promoções (útil para testes)
     */
    public void limpar() {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM Promocao").executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
    }

    /**
     * Obter quantidade de promoções publicadas
     * @return número de promoções
     */
    public int contar() {
        TypedQuery<Long> query = em.createQuery("SELECT COUNT(p) FROM Promocao p WHERE p.status = 'PUBLICADA'", Long.class);
        return query.getSingleResult().intValue();
    }

    /**
     * Fechar EntityManager (chame no shutdown da aplicação)
     */
    public static void fechar() {
        if (em != null) em.close();
        if (emf != null) emf.close();
    }
}

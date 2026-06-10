package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Usuario;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends CrudRepository<Usuario, Long> {
}

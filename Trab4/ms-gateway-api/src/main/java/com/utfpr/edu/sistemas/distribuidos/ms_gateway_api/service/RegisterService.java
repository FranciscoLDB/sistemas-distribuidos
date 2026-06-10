package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.PromotionController;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.CreateLojaRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.UserInteresseRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.CreateUserRequest;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.CategoriaRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.LojaRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.repository.UsuarioRepository;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Categoria;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Loja;
import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.util.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class RegisterService {

    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final LojaRepository lojaRepository;

    public String cadastrarUsuario(CreateUserRequest request, String assinatura, String requisitor) {
        List<Categoria> categorias = getCategoriasByIds(request.categoriasInteresse());
        Usuario usuario = mapToUsuario(request, categorias);
        usuarioRepository.save(usuario);
        return "Usuário cadastrado com sucesso!";
    }

    public String cadastrarLoja(CreateLojaRequest request, String assinatura, String requisitor) {
        Loja loja = mapToLoja(request);
        lojaRepository.save(loja);
        return "Loja cadastrada com sucesso!";
    }

    public List<Long> cadastrarInteresseUsuario(UserInteresseRequest request, String assinatura, String requisitor) {
        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado com id: " + request.usuarioId()));
        log.info("[SERVICE][USUARIO][INTERESSE][CADASTRAR] Cadastrando interesse do usuário com id: {}, categorias: {}", request.usuarioId(), request.categoriasInteresse());
        log.info("[SERVICE][USUARIO][INTERESSE][CADASTRAR] Categorias a serem adicionadas: {}", request.categoriasInteresse());
        List<Categoria> categorias = getCategoriasByIds(request.categoriasInteresse());
        categorias.forEach(usuario::adicionarInteresse);
        usuarioRepository.save(usuario);
        PromotionController.atualizarInteressesConsumidor(usuario.getId().toString(), usuario.getCategorias().stream().map(Categoria::getNome).toList());
        log.info("[SERVICE][USUARIO][INTERESSE][CADASTRAR] Interesses do usuário atualizados: {}", usuario.getCategorias());
        return usuario.getCategorias().stream().map(Categoria::getId).toList();
    }

    public List<Long> removerInteresseUsuario(UserInteresseRequest request, String assinatura, String requisitor) {
        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado com id: " + request.usuarioId()));
        log.info("[SERVICE][USUARIO][INTERESSE][REMOVER] Removendo interesse do usuário com id: {}, categorias: {}", request.usuarioId(), request.categoriasInteresse());
        log.info("[SERVICE][USUARIO][INTERESSE][REMOVER] Categorias a serem removidas: {}", request.categoriasInteresse());
        List<Categoria> categorias = getCategoriasByIds(request.categoriasInteresse());
        usuario.getCategorias().removeAll(categorias);
        usuarioRepository.save(usuario);
        PromotionController.atualizarInteressesConsumidor(usuario.getId().toString(), usuario.getCategorias().stream().map(Categoria::getNome).toList());
        log.info("[SERVICE][USUARIO][INTERESSE][REMOVER] Interesses do usuário atualizados: {}", usuario.getCategorias());
        return usuario.getCategorias().stream().map(Categoria::getId).toList();
    }

    private List<Categoria> getCategoriasByIds(List<Long> categoriaIds) {
        return categoriaIds.stream()
                .map(id -> categoriaRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Categoria não encontrada: " + id)))
                .toList();
    }

    private Usuario mapToUsuario(CreateUserRequest request, List<Categoria> categorias) {
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setSenha(request.senha());
        usuario.setCategorias(categorias);
        return usuario;
    }

    private Loja mapToLoja(CreateLojaRequest request) {
        Loja loja = new Loja();
        loja.setNome(request.nome());
        loja.setEmail(request.email());
        loja.setImagemUrl(request.imagemUrl());
        return loja;
    }
}

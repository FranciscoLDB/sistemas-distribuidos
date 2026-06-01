package com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.service;

import com.utfpr.edu.sistemas.distribuidos.ms_gateway_api.input.dto.CreateUserRequest;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    public String cadastrarUsuario(CreateUserRequest request, String assinatura, String requisitor) {
        // Lógica para cadastrar o usuário
        // Aqui você pode chamar um repositório para salvar o usuário no banco de dados
        // e também pode realizar validações, como verificar se o email já existe, etc.

        return "Usuário cadastrado com sucesso!";
    }

    public String cadastrarLoja() {
        // Lógica para cadastrar a loja
        return "Loja cadastrada com sucesso!";
    }
}

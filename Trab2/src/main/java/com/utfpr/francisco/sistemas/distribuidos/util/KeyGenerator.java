package com.utfpr.francisco.sistemas.distribuidos.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

/**
 * Gera as chaves RSA para todos os microsserviços
 */
public class KeyGenerator {

    public static void main(String[] args) {
        String[] services = {"Gateway", "Promocao", "Ranking", "Notificacao"};

        try {
            // Criar diretório se não existir
            Files.createDirectories(Paths.get("keys"));

            for (String service : services) {
                System.out.println("Gerando chaves para: " + service);

                // Gerar par de chaves
                KeyPair keyPair = KeyManager.generateKeyPair();

                // Salvar chaves
                KeyManager.savePrivateKey(service, keyPair.getPrivate());
                KeyManager.savePublicKey(service, keyPair.getPublic());

                System.out.println("✓ Chaves geradas e salvas para: " + service);
            }

            System.out.println("\n✓ Todas as chaves foram geradas com sucesso!");
            System.out.println("Arquivos salvos em: ./keys/");

        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("Erro ao gerar chaves: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

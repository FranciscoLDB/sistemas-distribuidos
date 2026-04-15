package com.utfpr.francisco.sistemas.distribuidos.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Gerenciador de chaves RSA para os microsserviços
 */
public class KeyManager {

    private static final String KEYS_DIRECTORY = "keys";
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;

    /**
     * Carregar chave privada do arquivo
     */
    public static PrivateKey loadPrivateKey(String serviceName) throws Exception {
        String keyPath = KEYS_DIRECTORY + "/" + serviceName + "_private.key";
        byte[] encoded = Files.readAllBytes(Paths.get(keyPath));
        String privateKeyPEM = new String(encoded)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decodedKey = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
        return kf.generatePrivate(spec);
    }

    /**
     * Carregar chave pública do arquivo
     */
    public static PublicKey loadPublicKey(String serviceName) throws Exception {
        String keyPath = KEYS_DIRECTORY + "/" + serviceName + "_public.key";
        byte[] encoded = Files.readAllBytes(Paths.get(keyPath));
        String publicKeyPEM = new String(encoded)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decodedKey = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
        return kf.generatePublic(spec);
    }

    /**
     * Salvar chave privada em arquivo
     */
    public static void savePrivateKey(String serviceName, PrivateKey privateKey) throws IOException {
        String keyContent = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(privateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----";

        Files.write(Paths.get(KEYS_DIRECTORY + "/" + serviceName + "_private.key"), keyContent.getBytes());
    }

    /**
     * Salvar chave pública em arquivo
     */
    public static void savePublicKey(String serviceName, PublicKey publicKey) throws IOException {
        String keyContent = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(publicKey.getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        Files.write(Paths.get(KEYS_DIRECTORY + "/" + serviceName + "_public.key"), keyContent.getBytes());
    }

    /**
     * Gerar novo par de chaves
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyGen.initialize(KEY_SIZE);
        return keyGen.generateKeyPair();
    }
}

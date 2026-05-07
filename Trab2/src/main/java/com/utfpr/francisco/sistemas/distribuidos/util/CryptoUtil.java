package com.utfpr.francisco.sistemas.distribuidos.util;

import com.utfpr.francisco.sistemas.distribuidos.model.Evento;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utilitário para assinar e validar mensagens usando criptografia assimétrica RSA
 */
public class CryptoUtil {

    private static final String ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 2048;

    /**
     * Gerar par de chaves RSA
     * @return KeyPair contendo chave privada e pública
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyGen.initialize(KEY_SIZE);
        return keyGen.generateKeyPair();
    }

    /**
     * Assinar uma mensagem com a chave privada
     * @param message mensagem a ser assinada
     * @param privateKey chave privada
     * @return assinatura em Base64
     */
    public static String sign(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    /**
     * Validar assinatura com a chave pública
     * @param message mensagem original
     * @param signatureBase64 assinatura em Base64
     * @param publicKey chave pública
     * @return true se válida, false caso contrário
     */
    public static boolean verify(String message, String signatureBase64, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        return signature.verify(signatureBytes);
    }

    /**
     * Converter chave pública para String (Base64)
     * @param publicKey chave pública
     * @return string Base64 da chave
     */
    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Converter chave privada para String (Base64)
     * @param privateKey chave privada
     * @return string Base64 da chave
     */
    public static String privateKeyToString(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Restaurar chave pública a partir de String (Base64)
     * @param publicKeyString string Base64 da chave pública
     * @return chave pública
     */
    public static PublicKey stringToPublicKey(String publicKeyString) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(publicKeyString);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
        return kf.generatePublic(spec);
    }

    /**
     * Validar assinatura de um evento
     */
    public static boolean validarAssinatura(Evento evento) {
        try {
            // Buscar a chave pública do produtor
            PublicKey publicKey = KeyManager.loadPublicKey(evento.getProdutor());

            // Validar a assinatura
            boolean isValid = CryptoUtil.verify(evento.getConteudo(), evento.getAssinatura(), publicKey);

            if (isValid) {
                System.out.println("✓ Assinatura válida do produtor: " + evento.getProdutor());
            } else {
                System.err.println("❌ Assinatura inválida do produtor: " + evento.getProdutor());
            }

            return isValid;

        } catch (Exception e) {
            System.err.println("❌ Erro ao validar assinatura: " + e.getMessage());
            return false;
        }
    }
}


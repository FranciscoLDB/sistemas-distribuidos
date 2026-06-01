package com.utfpr.edu.sistemas.distribuidos.ms_notificacao.service;

import com.resend.*;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class EmailService {

    private final String RESEND_API_KEY = "re_8arppFzn_JyUcR5Etvgw931A2d6JZnMuG";

    @Async
    public void enviarEmail(String para, String assunto, String corpo) {
        Resend resend = new Resend(RESEND_API_KEY);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("Acme <onboarding@resend.dev>")
                .to(para)
                .subject(assunto)
                .html(corpo)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            log.info("[EMAIL] Email enviado para {}: {}", para, assunto);
        } catch (ResendException e) {
            log.error("[EMAIL] Erro ao enviar email para {}: {}", para, e.getMessage());
        }

    }
}

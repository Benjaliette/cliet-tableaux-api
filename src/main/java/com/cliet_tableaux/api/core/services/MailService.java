package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import com.cliet_tableaux.api.core.exceptions.GlobalExceptionHandler;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final Resend resend;

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Value("${mail.contact.destinataire}")
  private String destinataire;

  @Value("${mail.contact.expediteur}")
  private String expediteur;

  public MailService(@Value("${RESEND_API_KEY}") String apiKey) {
    this.resend = new Resend(apiKey);
  }

  public void envoyerMessageContact(ContactDto dto) {
    CreateEmailOptions params = CreateEmailOptions.builder()
        .from("Contact CLIET Tableaux <" + expediteur + ">")
        .to(destinataire)
        .subject(dto.message())
        .html(construireCorpsEmail(dto))
        .build();

    try {
      CreateEmailResponse data = resend.emails().send(params);
      logger.info("Email envoyé, id : {}", data.getId());
    } catch (ResendException e) {
      logger.info("Erreur envoi email", e);
    }
  }

  private String construireCorpsEmail(ContactDto dto) {
    return """
        <html>
            <body>
                <h2>Nouveau message de contact</h2>
                <p><strong>De :</strong> %s</p>
                <p><strong>Email :</strong> %s</p>
                <hr/>
                <p>%s</p>
            </body>
        </html>
        """.formatted(dto.name(), dto.email(), dto.message());
  }
}

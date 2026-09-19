package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import com.cliet_tableaux.api.core.dtos.CustomRequestDto;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

@Service
public class MailService {

  private final Resend resend;
  private final ReferenceImageValidator referenceImageValidator;

  private static final Logger logger = LoggerFactory.getLogger(MailService.class);

  @Value("${mail.contact.destinataire}")
  private String destinataire;

  @Value("${mail.contact.expediteur}")
  private String expediteur;

  public MailService(@Value("${RESEND_API_KEY}") String apiKey, ReferenceImageValidator referenceImageValidator) {
    this.resend = new Resend(apiKey);
    this.referenceImageValidator = referenceImageValidator;
  }

  public void envoyerMessageContact(ContactDto dto) {
    CreateEmailOptions params = CreateEmailOptions.builder()
        .from("Contact CLIET Tableaux <" + expediteur + ">")
        .to(destinataire)
        .replyTo(dto.email())
        .subject(dto.message())
        .html(construireCorpsEmail(dto))
        .build();

    try {
      CreateEmailResponse data = resend.emails().send(params);
      logger.info("Email envoyé, id : {}", data.getId());
    } catch (ResendException e) {
      logger.error("Erreur envoi email", e);
    }
  }

  String construireCorpsEmail(ContactDto dto) {
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
        """.formatted(
        HtmlUtils.htmlEscape(dto.name()),
        HtmlUtils.htmlEscape(dto.email()),
        HtmlUtils.htmlEscape(dto.message()));
  }

  public void envoyerDemandeSurMesure(CustomRequestDto dto, MultipartFile referenceImage) {
    Attachment attachment = referenceImageValidator.validateAndBuildAttachment(referenceImage);

    envoyerDemandeSurMesureAdmin(dto, attachment);
    envoyerAccuseReceptionDemandeSurMesure(dto);
  }

  private void envoyerDemandeSurMesureAdmin(CustomRequestDto dto, Attachment attachment) {
    CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
        .from("Demande sur-mesure CLIET Tableaux <" + expediteur + ">")
        .to(destinataire)
        .replyTo(dto.email())
        .subject("Nouvelle demande de tableau sur-mesure de " + dto.name())
        .html(construireCorpsEmailDemandeSurMesure(dto, attachment != null));

    if (attachment != null) {
      builder.addAttachment(attachment);
    }

    envoyer(builder.build(), "demande sur-mesure (admin)");
  }

  private void envoyerAccuseReceptionDemandeSurMesure(CustomRequestDto dto) {
    CreateEmailOptions params = CreateEmailOptions.builder()
        .from("CLIET Tableaux <" + expediteur + ">")
        .to(dto.email())
        .replyTo(destinataire)
        .subject("Nous avons bien reçu votre demande de tableau sur-mesure")
        .html("""
            <html>
                <body>
                    <p>Bonjour %s,</p>
                    <p>Nous avons bien reçu votre demande de tableau sur-mesure et reviendrons vers vous rapidement.</p>
                    <hr/>
                    <p><strong>Description de votre projet :</strong></p>
                    <p>%s</p>
                </body>
            </html>
            """.formatted(
            HtmlUtils.htmlEscape(dto.name()),
            HtmlUtils.htmlEscape(dto.description())))
        .build();

    envoyer(params, "accusé de réception demande sur-mesure");
  }

  private void envoyer(CreateEmailOptions params, String libelle) {
    try {
      CreateEmailResponse data = resend.emails().send(params);
      logger.info("Email envoyé ({}), id : {}", libelle, data.getId());
    } catch (ResendException e) {
      logger.error("Erreur envoi email ({})", libelle, e);
      throw new RuntimeException("Échec de l'envoi de l'email : " + libelle, e);
    }
  }

  String construireCorpsEmailDemandeSurMesure(CustomRequestDto dto, boolean hasAttachment) {
    return """
        <html>
            <body>
                <h2>Nouvelle demande de tableau sur-mesure</h2>
                <p><strong>Nom :</strong> %s</p>
                <p><strong>Email :</strong> %s</p>
                <p><strong>Téléphone :</strong> %s</p>
                <p><strong>Budget :</strong> %s</p>
                <hr/>
                <p><strong>Description du projet :</strong></p>
                <p>%s</p>
                <hr/>
                <p><strong>Image de référence :</strong> %s</p>
            </body>
        </html>
        """.formatted(
        HtmlUtils.htmlEscape(dto.name()),
        HtmlUtils.htmlEscape(dto.email()),
        dto.phone() != null ? HtmlUtils.htmlEscape(dto.phone()) : "non renseigné",
        dto.budget() != null ? HtmlUtils.htmlEscape(dto.budget()) : "non renseigné",
        HtmlUtils.htmlEscape(dto.description()),
        hasAttachment ? "en pièce jointe" : "aucune");
  }
}

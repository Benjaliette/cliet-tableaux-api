package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String destinationEmail;

    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${app.mail.contact-form.destination}") String destinationEmail) {
        this.mailSender = mailSender;
        this.destinationEmail = destinationEmail;
    }

    @Override
    public void sendContactFormEmail(ContactDto contactDto) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("benjamin.liet.dev@gmail.com");
        message.setTo(destinationEmail);
        message.setReplyTo(contactDto.email());

        message.setSubject("Nouveau message du contact : " + contactDto.email());

        String emailBody = String.format(
                "Vous avez reçu un nouveau message de %s (%s).\n\n" +
                        "Message :\n%s",
                contactDto.name(),
                contactDto.email(),
                contactDto.message()
        );
        message.setText(emailBody);

        try {
            mailSender.send(message);
            log.info("Email de contact de {} envoyé avec succès à {}", contactDto.email(), destinationEmail);
        } catch (MailException e) {
            log.error("Erreur lors de l'envoi de l'email de contact pour {}", contactDto.email(), e);
        }
    }
}


package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.exceptions.UnsupportedFileTypeException;
import com.resend.services.emails.model.Attachment;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

// Valide l'image de référence jointe à une demande de tableau sur-mesure et la convertit en
// pièce jointe Resend. Le type MIME est déterminé par lecture des octets de signature du
// fichier (magic bytes), jamais par le nom de fichier ou le Content-Type déclaré par le
// client, qui peuvent tous deux être falsifiés.
@Service
public class ReferenceImageValidator {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    // Retourne null si aucune image n'a été fournie (champ optionnel).
    public Attachment validateAndBuildAttachment(MultipartFile referenceImage) {
        if (referenceImage == null || referenceImage.isEmpty()) {
            return null;
        }

        if (referenceImage.getSize() > MAX_SIZE_BYTES) {
            throw new ValidationException("referenceImage : le fichier dépasse la taille maximale autorisée (5 Mo)");
        }

        byte[] content = readBytes(referenceImage);
        String detectedExtension = detectExtension(content);
        if (detectedExtension == null) {
            throw new UnsupportedFileTypeException(
                    "referenceImage : format de fichier non supporté, seuls JPEG, PNG et WEBP sont acceptés");
        }

        return Attachment.builder()
                .fileName(sanitizeFileName(referenceImage.getOriginalFilename(), detectedExtension))
                .content(Base64.getEncoder().encodeToString(content))
                .build();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le fichier referenceImage", e);
        }
    }

    private String detectExtension(byte[] content) {
        if (matches(content, JPEG_MAGIC, 0)) {
            return "jpg";
        }
        if (matches(content, PNG_MAGIC, 0)) {
            return "png";
        }
        if (matches(content, RIFF_MAGIC, 0) && matches(content, WEBP_MAGIC, 8)) {
            return "webp";
        }
        return null;
    }

    private boolean matches(byte[] content, byte[] magic, int offset) {
        if (content.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    // Le nom d'origine n'est utilisé que comme base cosmétique : il est nettoyé des caractères
    // de contrôle (CR/LF compris, pour éviter toute injection dans les en-têtes de l'email) et
    // l'extension est forcée à celle réellement détectée, pas à celle fournie par le client.
    private String sanitizeFileName(String originalFileName, String detectedExtension) {
        String base = "reference-image";
        if (originalFileName != null) {
            String withoutExtension = originalFileName.replaceAll("\\.[^.]*$", "");
            String cleaned = withoutExtension.replaceAll("[\\p{Cntrl}]", "").trim();
            if (!cleaned.isEmpty()) {
                base = cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
            }
        }
        return base + "." + detectedExtension;
    }
}

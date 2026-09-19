package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.exceptions.UnsupportedFileTypeException;
import com.resend.services.emails.model.Attachment;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceImageValidatorTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };
    private static final byte[] WEBP_BYTES = {
            'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'
    };

    private final ReferenceImageValidator validator = new ReferenceImageValidator();

    @Test
    void validateAndBuildAttachment_withNoFile_returnsNull() {
        assertThat(validator.validateAndBuildAttachment(null)).isNull();
    }

    @Test
    void validateAndBuildAttachment_withEmptyFile_returnsNull() {
        MockMultipartFile empty = new MockMultipartFile("referenceImage", "photo.jpg", "image/jpeg", new byte[0]);

        assertThat(validator.validateAndBuildAttachment(empty)).isNull();
    }

    @Test
    void validateAndBuildAttachment_withJpeg_returnsAttachment() {
        MockMultipartFile file = new MockMultipartFile("referenceImage", "photo.jpg", "image/jpeg", JPEG_BYTES);

        Attachment attachment = validator.validateAndBuildAttachment(file);

        assertThat(attachment).isNotNull();
        assertThat(attachment.getFileName()).endsWith(".jpg");
    }

    @Test
    void validateAndBuildAttachment_withPng_returnsAttachment() {
        MockMultipartFile file = new MockMultipartFile("referenceImage", "photo.png", "image/png", PNG_BYTES);

        Attachment attachment = validator.validateAndBuildAttachment(file);

        assertThat(attachment).isNotNull();
        assertThat(attachment.getFileName()).endsWith(".png");
    }

    @Test
    void validateAndBuildAttachment_withWebp_returnsAttachment() {
        MockMultipartFile file = new MockMultipartFile("referenceImage", "photo.webp", "image/webp", WEBP_BYTES);

        Attachment attachment = validator.validateAndBuildAttachment(file);

        assertThat(attachment).isNotNull();
        assertThat(attachment.getFileName()).endsWith(".webp");
    }

    // Le type réel des octets (texte brut) prime sur l'extension et le Content-Type déclarés,
    // tous deux falsifiables.
    @Test
    void validateAndBuildAttachment_withSpoofedExtensionAndContentType_isRejected() {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "referenceImage", "photo.jpg", "image/jpeg", "ceci n'est pas une image".getBytes());

        assertThatThrownBy(() -> validator.validateAndBuildAttachment(fakeImage))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void validateAndBuildAttachment_withFileTooLarge_isRejected() {
        byte[] tooLarge = new byte[6 * 1024 * 1024];
        System.arraycopy(JPEG_BYTES, 0, tooLarge, 0, JPEG_BYTES.length);
        MockMultipartFile file = new MockMultipartFile("referenceImage", "photo.jpg", "image/jpeg", tooLarge);

        assertThatThrownBy(() -> validator.validateAndBuildAttachment(file))
                .isInstanceOf(ValidationException.class);
    }
}

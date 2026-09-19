package com.cliet_tableaux.api.core.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import com.cliet_tableaux.api.core.dtos.CustomRequestDto;
import org.junit.jupiter.api.Test;

class MailServiceTest {

    private static final String XSS_PAYLOAD = "<img src=x onerror=alert(1)>";

    private final MailService mailService = new MailService("re_test_dummy", new ReferenceImageValidator());

    @Test
    void construireCorpsEmail_escapesHtmlInUserFields() {
        ContactDto dto = new ContactDto(XSS_PAYLOAD, "attaquant@example.com", XSS_PAYLOAD);

        String html = mailService.construireCorpsEmail(dto);

        assertThat(html).doesNotContain(XSS_PAYLOAD);
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void construireCorpsEmailDemandeSurMesure_escapesHtmlInAllUserFields() {
        CustomRequestDto dto = new CustomRequestDto(XSS_PAYLOAD, "attaquant@example.com", XSS_PAYLOAD,
                XSS_PAYLOAD, XSS_PAYLOAD);

        String html = mailService.construireCorpsEmailDemandeSurMesure(dto, false);

        assertThat(html).doesNotContain(XSS_PAYLOAD);
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void construireCorpsEmailDemandeSurMesure_withNullOptionalFields_doesNotFail() {
        CustomRequestDto dto = new CustomRequestDto("Nom normal", "normal@example.com", null,
                "Description normale", null);

        String html = mailService.construireCorpsEmailDemandeSurMesure(dto, true);

        assertThat(html).contains("non renseigné");
        assertThat(html).contains("en pièce jointe");
    }
}

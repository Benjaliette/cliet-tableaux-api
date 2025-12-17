package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.dtos.ContactDto;

public interface EmailService {
    void sendContactFormEmail(ContactDto contactDto);
}

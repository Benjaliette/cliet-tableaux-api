package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.CloudinarySignatureDto;
import com.cliet_tableaux.api.core.dtos.CloudinarySignatureRequestDto;
import com.cloudinary.Cloudinary;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/v1/cloudinary-signature")
public class CloudinaryController {

    private final Cloudinary cloudinary;

    public CloudinaryController(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @PostMapping
    public CloudinarySignatureDto getSignature(@RequestBody CloudinarySignatureRequestDto request) {
        long timestamp = System.currentTimeMillis() / 1000L;

        Map<String, Object> paramsToSign = new TreeMap<>();
        if (request.folder() != null && !request.folder().isEmpty()) {
            paramsToSign.put("folder", request.folder());
        }
        paramsToSign.put("timestamp", timestamp);
        paramsToSign.put("upload_preset", "ml_default");

        String signature = cloudinary.apiSignRequest(paramsToSign, cloudinary.config.apiSecret);

        return new CloudinarySignatureDto(timestamp, signature);
    }
}

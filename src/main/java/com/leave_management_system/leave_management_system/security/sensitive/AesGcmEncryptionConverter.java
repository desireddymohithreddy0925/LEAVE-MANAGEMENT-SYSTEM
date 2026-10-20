package com.leave_management_system.leave_management_system.security.sensitive;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Converter
@Component
public class AesGcmEncryptionConverter implements AttributeConverter<String, String> {

    private static CryptoService staticCryptoService;

    public AesGcmEncryptionConverter() {
    }

    @Autowired
    public void setCryptoService(@Lazy CryptoService cryptoService) {
        AesGcmEncryptionConverter.staticCryptoService = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (staticCryptoService != null) {
            return staticCryptoService.encrypt(attribute);
        }
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (staticCryptoService != null) {
            return staticCryptoService.decrypt(dbData);
        }
        return dbData;
    }
}

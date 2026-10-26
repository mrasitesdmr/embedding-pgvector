package com.embedding.pgvector.source.domain.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Yüklenen dosyanın gönderilmiş ve boş olmadığını denetleyerek dosya doğrulama kuralını uygular.
 */
public class NotEmptyFileValidator implements ConstraintValidator<NotEmptyFile, MultipartFile> {

    @Override
    public boolean isValid(@Nullable MultipartFile file, ConstraintValidatorContext context) {
        return file != null && !file.isEmpty();
    }
}

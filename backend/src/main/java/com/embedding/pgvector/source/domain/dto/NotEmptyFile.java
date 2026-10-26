package com.embedding.pgvector.source.domain.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Yüklenen dosyanın eksik ya da boş olmamasını isteyen doğrulama kuralını tanımlar.
 */
@Documented
@Constraint(validatedBy = NotEmptyFileValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotEmptyFile {

    String message() default "must not be empty";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

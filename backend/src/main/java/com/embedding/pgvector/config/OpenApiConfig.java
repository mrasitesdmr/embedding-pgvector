package com.embedding.pgvector.config;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * Üretilen OpenAPI belgesini REST sözleşmesine uyacak şekilde düzenler; Problem Details hata şemasını tamamlar.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String API_TITLE = "Embedding ve pgvector API";
    private static final String API_VERSION = "0.4.0";
    private static final List<String> ERROR_CODES = List.of(
            "VALIDATION_FAILED",
            "BAD_REQUEST",
            "NOT_FOUND",
            "METHOD_NOT_ALLOWED",
            "NOT_ACCEPTABLE",
            "UNSUPPORTED_MEDIA_TYPE",
            "EMBEDDING_UNAVAILABLE",
            "EMBEDDING_MODEL_NOT_FOUND",
            "INTERNAL_ERROR",
            "WORKSPACE_NOT_FOUND",
            "SOURCE_NOT_FOUND",
            "CHUNK_NOT_FOUND",
            "INGESTION_JOB_NOT_FOUND",
            "SOURCE_NAME_TAKEN",
            "SOURCE_LIMIT_REACHED",
            "SOURCE_KIND_MISMATCH",
            "INGESTION_IN_PROGRESS",
            "EMBEDDING_MODEL_MISMATCH",
            "FILE_TOO_LARGE",
            "UNSUPPORTED_FILE_TYPE",
            "UNSUPPORTED_FILE_ENCODING");

    private static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";
    private static final String VIOLATION_SCHEMA = "Violation";
    private static final String SOURCE_RESPONSE_SCHEMA = "SourceResponse";
    private static final String LATEST_JOB_PROPERTY = "latestJob";

    @Bean
    public OpenApiCustomizer problemDetailSchemaCustomizer() {
        return openApi -> {
            openApi.info(new Info().title(API_TITLE).version(API_VERSION));
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            Schema<?> problemDetail = schemas.get(PROBLEM_DETAIL_SCHEMA);
            if (problemDetail == null) {
                return;
            }
            schemas.put(VIOLATION_SCHEMA, violationSchema());
            customizeProblemDetail(problemDetail);
        };
    }

    @Bean
    public OpenApiCustomizer nullableLatestJobCustomizer() {
        return openApi -> {
            Schema<?> sourceResponse = openApi.getComponents().getSchemas().get(SOURCE_RESPONSE_SCHEMA);
            if (sourceResponse == null || sourceResponse.getProperties() == null) {
                return;
            }
            Schema<?> latestJob = sourceResponse.getProperties().get(LATEST_JOB_PROPERTY);
            if (latestJob == null || latestJob.get$ref() == null) {
                return;
            }
            sourceResponse.addProperty(LATEST_JOB_PROPERTY, nullableReference(latestJob));
        };
    }

    @Bean
    public OperationCustomizer requiredMultipartPartsCustomizer() {
        return (operation, handlerMethod) -> {
            Content content = operation.getRequestBody() == null ? null : operation.getRequestBody().getContent();
            MediaType multipart = content == null
                    ? null
                    : content.get(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE);
            if (multipart == null || multipart.getSchema() == null) {
                return operation;
            }
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                RequestPart part = parameter.getParameterAnnotation(RequestPart.class);
                Parameter documentation = parameter.getParameterAnnotation(Parameter.class);
                if (part != null && documentation != null && documentation.required()) {
                    multipart.getSchema().addRequiredItem(part.name());
                }
            }
            return operation;
        };
    }

    private static void customizeProblemDetail(Schema<?> problemDetail) {
        problemDetail.getProperties().remove("properties");
        problemDetail.addProperty("instance", new StringSchema());
        problemDetail.addProperty("errorCode", new StringSchema()._enum(ERROR_CODES).description("Stable error code"));
        problemDetail.addProperty("violations", new ArraySchema()
                .items(new Schema<>().$ref("#/components/schemas/" + VIOLATION_SCHEMA))
                .description("Only for VALIDATION_FAILED"));
        problemDetail.description("RFC 9457 Problem Details with the project's errorCode extension");
        problemDetail.required(List.of("type", "title", "status", "errorCode"));
    }

    private static Schema<?> nullableReference(Schema<?> reference) {
        return new Schema<>()
                .description(reference.getDescription())
                .anyOf(List.of(new Schema<>().$ref(reference.get$ref()), new Schema<>().types(Set.of("null"))));
    }

    private static Schema<?> violationSchema() {
        return new ObjectSchema()
                .addProperty("field", new StringSchema().example("text"))
                .addProperty("message", new StringSchema().example("must not be blank")
                        .description("Fixed English message"))
                .required(List.of("field", "message"));
    }
}

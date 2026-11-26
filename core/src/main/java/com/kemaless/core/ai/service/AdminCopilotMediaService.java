package com.kemaless.core.ai.service;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.dto.api.AdminCopilotAttachmentRequest;
import com.kemaless.core.ai.dto.api.AdminCopilotMediaUploadResponse;
import com.kemaless.core.ai.exception.MediaStorageUnavailableException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectResponse;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminCopilotMediaService {

    private final ObjectProvider<MinioClient> minioClient;
    private final PlatformAiProperties platformAiProperties;

    public AdminCopilotMediaService(
            ObjectProvider<MinioClient> minioClient,
            PlatformAiProperties platformAiProperties
    ) {
        this.minioClient = minioClient;
        this.platformAiProperties = platformAiProperties;
    }

    public AdminCopilotMediaUploadResponse upload(UUID userId, MultipartFile file) {
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            throw new MediaStorageUnavailableException("MinIO недоступен (клиент не создан).");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл для загрузки не передан");
        }

        String bucket = bucket();
        String publicBase = publicBaseUrl();

        String originalFileName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String safeFileName = sanitizeFileName(originalFileName);
        String objectKey = "admin-copilot/" + userId + "/" + UUID.randomUUID() + "/" + safeFileName;
        String mimeType = normalizeMimeType(file.getContentType());

        try {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1L)
                            .contentType(mimeType)
                            .build()
            );
        } catch (Exception exception) {
            throw new MediaStorageUnavailableException("Не удалось загрузить файл в MinIO: " + exception.getMessage());
        }

        return new AdminCopilotMediaUploadResponse(
                objectKey,
                publicBase + "/" + bucket + "/" + objectKey,
                safeFileName,
                mimeType,
                inferFileType(mimeType)
        );
    }

    public List<Media> toMedia(List<AdminCopilotAttachmentRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        return attachments.stream()
                .map(this::toMedia)
                .toList();
    }

    private Media toMedia(AdminCopilotAttachmentRequest attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Вложение не задано");
        }
        String mimeTypeValue = normalizeMimeType(attachment.mimeType());
        MimeType mimeType = MimeTypeUtils.parseMimeType(mimeTypeValue);

        if (attachment.objectKey() != null && !attachment.objectKey().isBlank()) {
            String objectKey = validateObjectKey(attachment.objectKey());
            Resource resource = loadFromMinio(objectKey, attachment.fileName());
            return new Media(mimeType, resource);
        }

        if (attachment.url() != null && !attachment.url().isBlank()) {
            // Запрещаем внешние HTTP-запросы (редиректы, DNS rebinding, IPv6/спецдиапазоны) и грузим только из доверенного MinIO.
            String objectKey = extractTrustedStorageObjectKey(attachment.url());
            Resource resource = loadFromMinio(objectKey, attachment.fileName());
            return new Media(mimeType, resource);
        }

        throw new IllegalArgumentException("Для вложения требуется objectKey или url");
    }

    private String validateObjectKey(String rawObjectKey) {
        String objectKey = rawObjectKey == null ? "" : rawObjectKey.trim();
        if (!objectKey.startsWith("admin-copilot/")
                || objectKey.length() <= "admin-copilot/".length()
                || objectKey.contains("..")
                || objectKey.contains("\\")
                || objectKey.contains("//")
                || objectKey.contains("?")
                || objectKey.contains("#")
                || objectKey.contains("%")
                || containsWhitespaceOrControl(objectKey)) {
            throw new IllegalArgumentException("Недопустимый objectKey вложения");
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Недопустимый objectKey вложения");
            }
        }
        return objectKey;
    }

    private String extractTrustedStorageObjectKey(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()
                || trimmed.contains("\\")
                || trimmed.contains("?")
                || trimmed.contains("#")
                || trimmed.contains("%")
                || containsWhitespaceOrControl(trimmed)) {
            throw new IllegalArgumentException("Недопустимый URL вложения");
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Некорректный URL вложения", exception);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || (!"https".equals(scheme) && !"http".equals(scheme))) {
            throw new IllegalArgumentException("Разрешены только HTTP/HTTPS URL доверенного хранилища");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("URL вложения содержит недопустимые компоненты");
        }

        URI baseUri = parseTrustedBaseUri();
        if (!scheme.equalsIgnoreCase(baseUri.getScheme())
                || !canonicalHost(uri.getHost()).equals(canonicalHost(baseUri.getHost()))
                || effectivePort(uri) != effectivePort(baseUri)) {
            throw new IllegalArgumentException("Разрешена загрузка вложений только из доверенного хранилища MinIO");
        }

        String rawPath = uri.getRawPath();
        String normalizedPath = uri.normalize().getRawPath();
        if (rawPath == null || !rawPath.equals(normalizedPath) || rawPath.contains("..")) {
            throw new IllegalArgumentException("Недопустимый путь URL вложения");
        }

        String bucket = bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Бакет MinIO не настроен");
        }

        String basePath = trimTrailingSlash(baseUri.getRawPath());
        String expectedPrefix = basePath + "/" + bucket.trim() + "/";
        if (!rawPath.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("URL вложения не принадлежит настроенному бакету MinIO");
        }
        return validateObjectKey(rawPath.substring(expectedPrefix.length()));
    }

    private URI parseTrustedBaseUri() {
        String configuredPublicBase = publicBaseUrl();
        if (configuredPublicBase.isEmpty()) {
            throw new IllegalArgumentException("Доверенное хранилище MinIO public-base-url не настроен");
        }
        URI baseUri;
        try {
            baseUri = URI.create(configuredPublicBase);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Некорректный public-base-url MinIO", exception);
        }
        if (!baseUri.isAbsolute()
                || baseUri.getHost() == null
                || baseUri.getHost().isBlank()
                || baseUri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Некорректный public-base-url MinIO");
        }
        return baseUri;
    }

    private static String canonicalHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL вложения должен содержать host");
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean containsWhitespaceOrControl(String value) {
        return value.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch));
    }

    private Resource loadFromMinio(String objectKey, String fileName) {
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            throw new MediaStorageUnavailableException("MinIO недоступен для чтения вложения.");
        }
        try (GetObjectResponse object = client.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket())
                        .object(objectKey)
                        .build()
        )) {
            byte[] bytes = object.readAllBytes();
            return new NamedByteArrayResource(bytes, fileName);
        } catch (IOException exception) {
            throw new UncheckedIOException("Не удалось прочитать вложение из MinIO", exception);
        } catch (Exception exception) {
            throw new MediaStorageUnavailableException("Не удалось получить вложение из MinIO: " + exception.getMessage());
        }
    }

    private String bucket() {
        return platformAiProperties.getMedia().getMinio().getBucket();
    }

    private String publicBaseUrl() {
        return trimTrailingSlash(platformAiProperties.getMedia().getMinio().getPublicBaseUrl());
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String inferFileType(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return "image";
        }
        if (mimeType.startsWith("audio/")) {
            return "audio";
        }
        return "file";
    }

    private String sanitizeFileName(String fileName) {
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFKC);
        String ascii = normalized.replaceAll("[^\\p{ASCII}]", "_");
        String safe = ascii.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            return "upload.bin";
        }
        return safe;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray, "admin-copilot-media");
            this.fileName = (fileName == null || fileName.isBlank()) ? "attachment.bin" : fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}

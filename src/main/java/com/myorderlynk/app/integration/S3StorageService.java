package com.myorderlynk.app.integration;

import com.myorderlynk.app.exception.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String region;
    private final String endpointUrl;
    private final String publicBaseUrl;
    private final boolean publicReadAcl;
    /** False when AWS credentials/bucket are not configured — uploads then fail with a clean 503 rather than NPEs at startup. */
    private final boolean configured;

    @PostConstruct
    public void init() {
        if (!configured) {
            log.warn("S3 storage is not configured (app.aws.* unset) — image uploads are disabled.");
            return;
        }
        // Best-effort: a verification/creation failure (wrong region, missing permission,
        // bucket owned by another account) must NOT stop the application from booting.
        // Uploads will surface a clear error at request time instead.
        try {
            ensureBucketExists();
        } catch (SdkException e) {
            log.warn("Could not verify S3 bucket '{}' in region '{}' at startup ({}). "
                    + "Image uploads may fail until this is resolved.", bucket, region, e.getMessage());
        }
    }

    public S3StorageService(
            @Value("${app.aws.access-key:}") String accessKey,
            @Value("${app.aws.secret-key:}") String secretKey,
            @Value("${app.aws.region:us-east-1}") String region,
            @Value("${app.aws.s3-bucket:}") String bucket,
            @Value("${app.aws.endpoint-url:}") String endpointUrl,
            @Value("${app.aws.public-base-url:}") String publicBaseUrl,
            @Value("${app.aws.public-read-acl:true}") boolean publicReadAcl) {

        // A blank region yields an invalid Region.of("") and breaks request signing; fall back to a sane default.
        region = region == null || region.isBlank() ? "weur" : region;

        this.bucket = bucket;
        this.region = region;
        this.endpointUrl = endpointUrl;
        this.publicBaseUrl = publicBaseUrl;
        this.publicReadAcl = publicReadAcl;
        this.configured = !bucket.isBlank() && !accessKey.isBlank() && !secretKey.isBlank();

        if (!configured) {
            this.s3Client = null;
            this.s3Presigner = null;
            return;
        }

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (!endpointUrl.isBlank()) {
            // LocalStack (or any S3-compatible store) — force path-style so the bucket
            // name appears in the URL path rather than as a DNS subdomain.
            builder.endpointOverride(URI.create(endpointUrl))
                   .forcePathStyle(true);
            presignerBuilder.endpointOverride(URI.create(endpointUrl));
            log.info("S3 client using custom endpoint: {}", endpointUrl);
        }

        this.s3Client = builder.build();
        this.s3Presigner = presignerBuilder.build();
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Upload raw bytes to S3 as a publicly readable object and return its
     * stable, public HTTPS URL — suitable for persisting on a domain entity
     * (e.g. {@code Product.productImageUrl}) and serving directly to browsers.
     *
     * <p>The object is created with a {@code public-read} ACL by default
     * ({@code app.aws.public-read-acl}). If the bucket has ACLs disabled
     * (Object Ownership = "Bucket owner enforced"), set that flag to {@code false}
     * and grant public read via a bucket policy instead.
     *
     * @param bytes       file contents
     * @param contentType MIME type stored on the object so the browser renders it inline
     * @param key         object key, e.g. {@code products/{vendorId}/{uuid}.jpg}
     * @return public HTTPS URL of the uploaded object
     */
    public String uploadPublic(byte[] bytes, String contentType, String key) {
        requireConfigured();
        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);
        if (publicReadAcl) {
            req.acl(ObjectCannedACL.PUBLIC_READ);
        }
        s3Client.putObject(req.build(), RequestBody.fromBytes(bytes));
        String url = publicUrl(key);
        log.info("Uploaded public object to {}", url);
        return url;
    }

    /** Build the public HTTPS URL for an object key, honouring CDN/custom-domain or custom-endpoint overrides. */
    private String publicUrl(String key) {
        if (!publicBaseUrl.isBlank()) {
            return stripTrailingSlash(publicBaseUrl) + "/" + key;
        }
        if (!endpointUrl.isBlank()) {
            // Path-style for LocalStack / S3-compatible stores.
            return stripTrailingSlash(endpointUrl) + "/" + bucket + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private void requireConfigured() {
        if (!configured) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        }
    }

    /**
     * Generate a time-limited HTTPS URL the platform's ingestion server can
     * fetch directly. The URL embeds the signed credentials; anyone holding it
     * can read the object until {@code ttl} elapses.
     *
     * @param s3Url s3://{bucket}/{key}
     * @param ttl   how long the URL stays valid
     * @return signed HTTPS URL pointing at the object
     */
    public String presignedUrl(String s3Url, Duration ttl) {
        requireConfigured();
        String key = s3Url.substring(("s3://" + bucket + "/").length());
        GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getReq)
                .build();
        return s3Presigner.presignGetObject(presign).url().toString();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 bucket '{}' already exists.", bucket);
        } catch (NoSuchBucketException e) {
            log.info("S3 bucket '{}' not found — creating it.", bucket);
            createBucket();
            log.info("S3 bucket '{}' created.", bucket);
        } catch (S3Exception e) {
            // headBucket returns 404→NoSuchBucket, but 403 (no permission / wrong owner) and
            // 301 (bucket lives in another region) come back as generic S3Exceptions. The bucket
            // may well exist and be usable; don't try to create it — just note it and move on.
            log.warn("Could not HEAD S3 bucket '{}' (status {}): {}. Assuming it exists.",
                    bucket, e.statusCode(), e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : e.getMessage());
        }
    }

    private void createBucket() {
        CreateBucketRequest.Builder req = CreateBucketRequest.builder().bucket(bucket);
        // Every region except us-east-1 requires an explicit location constraint on creation.
        if (!"us-east-1".equals(region)) {
            req.createBucketConfiguration(CreateBucketConfiguration.builder()
                    .locationConstraint(region)
                    .build());
        }
        s3Client.createBucket(req.build());
    }

    public boolean exists(String s3Url) {
        requireConfigured();
        String key = s3Url.substring(("s3://" + bucket + "/").length());
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Upload a local file to S3 under the given key.
     *
     * @return public S3 URL: s3://{bucket}/{key}
     */
    public String upload(Path localFile, String key) throws IOException {
        requireConfigured();
        log.info("Uploading {} to s3://{}/{}", localFile, bucket, key);
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.putObject(req, localFile);
        String url = "s3://" + bucket + "/" + key;
        log.info("Uploaded to {}", url);
        return url;
    }

    /**
     * Server-side copy of an existing object to a new key in the same bucket.
     *
     * @return public S3 URL of the new object: s3://{bucket}/{destKey}
     */
    public String copy(String srcS3Url, String destKey) {
        requireConfigured();
        String prefix = "s3://" + bucket + "/";
        if (!srcS3Url.startsWith(prefix)) {
            throw new IllegalArgumentException("Source URL is not in the configured bucket: " + srcS3Url);
        }
        String srcKey = srcS3Url.substring(prefix.length());
        log.info("Copying s3://{}/{} → s3://{}/{}", bucket, srcKey, bucket, destKey);
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(srcKey)
                .destinationBucket(bucket)
                .destinationKey(destKey)
                .build());
        return prefix + destKey;
    }

    /**
     * Best-effort delete for a {@code s3://bucket/key} URL. NoSuchKey is swallowed
     * so this is safe to call when the asset row may already point at a removed object.
     */
    public void delete(String s3Url) {
        if (s3Url == null || s3Url.isBlank()) return;
        requireConfigured();
        String prefix = "s3://" + bucket + "/";
        if (!s3Url.startsWith(prefix)) {
            log.warn("delete() ignoring non-matching url {}", s3Url);
            return;
        }
        String key = s3Url.substring(prefix.length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("Deleted s3://{}/{}", bucket, key);
        } catch (NoSuchKeyException e) {
            log.info("delete() — already gone: s3://{}/{}", bucket, key);
        }
    }

    /**
     * Returns the size of an S3 object in bytes (HEAD request).
     */
    public long contentLength(String s3Url) {
        requireConfigured();
        String key = s3Url.substring(("s3://" + bucket + "/").length());
        HeadObjectResponse head = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build());
        return head.contentLength();
    }

    /**
     * Opens an input stream for an S3 object, optionally for a byte range.
     * Pass {@code null} for {@code rangeHeader} to fetch the whole object;
     * otherwise pass an HTTP-style {@code "bytes=start-end"} string.
     *
     * <p>Caller owns the returned stream and must close it.
     */
    public ResponseInputStream<GetObjectResponse> openStream(String s3Url, String rangeHeader) {
        requireConfigured();
        String key = s3Url.substring(("s3://" + bucket + "/").length());
        GetObjectRequest.Builder builder = GetObjectRequest.builder().bucket(bucket).key(key);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            builder.range(rangeHeader);
        }
        return s3Client.getObject(builder.build());
    }

    /**
     * Download an S3 object to a temp file and return its path.
     *
     * @param s3Url  s3://{bucket}/{key}
     * @param suffix file suffix for the temp file (e.g. ".mp4")
     */
    public Path downloadToTemp(String s3Url, String suffix) throws IOException {
        requireConfigured();
        String key = s3Url.substring(("s3://" + bucket + "/").length());
        log.info("Downloading s3://{}/{} ...", bucket, key);
        Path temp = Files.createTempFile("s3-download-", suffix);
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try (var s3Stream = s3Client.getObject(req)) {
            Files.copy(s3Stream, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Downloaded to {}", temp);
        return temp;
    }
}
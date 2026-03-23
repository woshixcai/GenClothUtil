package com.UiUtil.shared.util;

/**
 * 火山引擎 TOS 对象存储工具类，封装图片上传逻辑，上传后去除签名参数直接返回永久公开 URL，
 * 同时提供将历史对象键或签名 URL 转换为公开 URL 的辅助方法。
 */
import cn.hutool.crypto.digest.DigestUtil;
import com.volcengine.tos.comm.HttpMethod;
import com.volcengine.tos.TOSClientConfiguration;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.auth.StaticCredentials;
import com.volcengine.tos.model.object.PutObjectInput;
import com.volcengine.tos.model.object.PutObjectOutput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ImageUtils {

    /** 配置文件兜底值，优先级低于同名环境变量 */
    @Value("${volc.tos.access-key:}")
    private String accessKeyConfig;

    @Value("${volc.tos.secret-key:}")
    private String secretKeyConfig;

    @Value("${volc.tos.endpoint:tos-cn-beijing.volces.com}")
    private String endpointConfig;

    @Value("${volc.tos.region:cn-beijing}")
    private String regionConfig;

    @Value("${volc.tos.bucket-name:}")
    private String bucketNameConfig;

    @Value("${volc.tos.presign-hours:1}")
    private long presignHours;

    private static String AliyunfileToBase64(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null) {
            contentType = "image/jpeg";
        }
        byte[] fileBytes = file.getBytes();
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);
        return String.format("data:image/%s;base64,%s", contentType, base64Content);
    }

    public static String fileToMd5(File file) throws IOException {
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件");
        }
        return DigestUtil.md5Hex(file);
    }

    public static String multipartFileToMd5(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        return DigestUtil.md5Hex(multipartFile.getBytes());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 私有工具方法：构建 TOS 客户端、获取桶名
    // ────────────────────────────────────────────────────────────────────────

    private TOSV2 buildClient() {
        String accessKey = resolve("VOLC_TOS_ACCESS_KEY", accessKeyConfig, "TOS AccessKey（volc.tos.access-key）");
        String secretKey = resolve("VOLC_TOS_SECRET_KEY", secretKeyConfig, "TOS SecretKey（volc.tos.secret-key）");
        String endpoint  = resolveOptional("VOLC_TOS_ENDPOINT", endpointConfig);
        String region    = resolveOptional("VOLC_TOS_REGION",   regionConfig);
        TOSClientConfiguration cfg = TOSClientConfiguration.builder()
                .region(region).endpoint(endpoint)
                .credentials(new StaticCredentials(accessKey, secretKey))
                .build();
        return new TOSV2ClientBuilder().build(cfg);
    }

    private String resolveBucket() {
        return resolve("VOLC_TOS_BUCKET_NAME", bucketNameConfig, "TOS 桶名（volc.tos.bucket-name）");
    }

    /**
     * 把“对象 Key / 已签名 URL / 可能已过期的预签名 URL”统一转换成可长期访问的公开 URL。
     * 前提：Bucket 已开启公开读。
     *
     * @param urlOrKey 例如：
     *                  1) uuid.jpg
     *                  2) https://bucket.endpoint/uuid.jpg?X-Algorithm=...
     */
    public String toPublicUrl(String urlOrKey) {
        if (urlOrKey == null) return null;
        String s = urlOrKey.trim();
        if (s.isEmpty()) return null;

        // URL：去掉查询参数（签名过期/失效不再影响）
        if (s.startsWith("http://") || s.startsWith("https://")) {
            int q = s.indexOf('?');
            return q >= 0 ? s.substring(0, q) : s;
        }
        // Key：按公开域名拼出 URL
        return buildPublicUrlFromKey(s);
    }

    private String buildPublicUrlFromKey(String objectKey) {
        String bucketName = resolveBucket();
        String endpointResolved = resolveOptional("VOLC_TOS_ENDPOINT", endpointConfig);

        String scheme = "https";
        String hostPart = endpointResolved;
        if (endpointResolved != null && endpointResolved.startsWith("http://")) {
            scheme = "http";
            hostPart = endpointResolved.substring(7);
        } else if (endpointResolved != null && endpointResolved.startsWith("https://")) {
            scheme = "https";
            hostPart = endpointResolved.substring(8);
        }
        if (hostPart == null || hostPart.trim().isEmpty()) {
            throw new RuntimeException("缺少 TOS endpoint 配置（volc.tos.endpoint / VOLC_TOS_ENDPOINT）");
        }
        return scheme + "://" + bucketName + "." + hostPart + "/" + objectKey;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 上传方法：返回可长期访问的公开 URL（无需预签名）
    // ────────────────────────────────────────────────────────────────────────

    public String uploadBytesToHuoShan(byte[] bytes, String originalFilename) throws Exception {
        String bucketName = resolveBucket();
        TOSV2 client = buildClient();
        try {
            String suffix = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String objectKey = UUID.randomUUID() + suffix;
            PutObjectInput input = new PutObjectInput()
                    .setBucket(bucketName).setKey(objectKey)
                    .setContent(new ByteArrayInputStream(bytes));
            client.putObject(input);
            // 用 SDK 生成一次“带签名 URL”，再去掉查询串（确保域名/路径格式与 TOS 一致）
            String signedUrl = client.preSignedURL(HttpMethod.GET, bucketName, objectKey, Duration.ofHours(presignHours));
            int q = signedUrl.indexOf('?');
            return q >= 0 ? signedUrl.substring(0, q) : signedUrl;
        } finally {
            client.close();
        }
    }

    public String uploadFileToHuoShan(MultipartFile file) throws Exception {
        String bucketName = resolveBucket();
        TOSV2 client = buildClient();
        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String objectKey = UUID.randomUUID() + suffix;
            PutObjectInput input = new PutObjectInput()
                    .setBucket(bucketName).setKey(objectKey)
                    .setContent(new ByteArrayInputStream(file.getBytes()));
            client.putObject(input);
            String signedUrl = client.preSignedURL(HttpMethod.GET, bucketName, objectKey, Duration.ofHours(presignHours));
            int q = signedUrl.indexOf('?');
            return q >= 0 ? signedUrl.substring(0, q) : signedUrl;
        } finally {
            client.close();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 预签名：将 Key 批量转换为有效期内的访问 URL（只建一次 TOS 客户端）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 批量生成预签名 URL，复用同一个 TOS 客户端，避免 N+1 建连开销。
     *
     * @param objectKeys TOS 对象 Key 集合（非 http 开头的字符串）
     * @return Key → 预签名 URL 的映射
     */
    public Map<String, String> preSignObjectKeys(Collection<String> objectKeys) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        if (objectKeys == null || objectKeys.isEmpty()) return result;
        String bucketName = resolveBucket();
        TOSV2 client = buildClient();
        try {
            for (String key : objectKeys) {
                result.put(key, client.preSignedURL(HttpMethod.GET, bucketName, key,
                        Duration.ofHours(presignHours)));
            }
        } finally {
            client.close();
        }
        return result;
    }

    /**
     * 单个 Key 生成预签名 URL（供 try-on 缓存场景使用）。
     */
    public String preSignObjectKey(String objectKey) throws Exception {
        String bucketName = resolveBucket();
        TOSV2 client = buildClient();
        try {
            return client.preSignedURL(HttpMethod.GET, bucketName, objectKey, Duration.ofHours(presignHours));
        } finally {
            client.close();
        }
    }

    private static String resolve(String envName, String configVal, String desc) {
        String env = System.getenv(envName);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        if (configVal != null && !configVal.trim().isEmpty()) {
            return configVal.trim();
        }
        throw new RuntimeException(
                "缺少配置：" + desc + "。请设置环境变量 " + envName + " 或在 application.yml 中配置。");
    }

    private static String resolveOptional(String envName, String configVal) {
        String env = System.getenv(envName);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return configVal;
    }
}

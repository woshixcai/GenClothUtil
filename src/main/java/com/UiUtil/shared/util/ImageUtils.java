package com.UiUtil.shared.util;

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

    /**
     * 普通File文件转MD5（32位小写）
     */
    public static String fileToMd5(File file) throws IOException {
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件");
        }
        return DigestUtil.md5Hex(file);
    }

    /**
     * MultipartFile（上传文件）转MD5（适配接口上传场景）
     */
    public static String multipartFileToMd5(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        return DigestUtil.md5Hex(multipartFile.getBytes());
    }

    /**
     * 上传MultipartFile到火山TOS对象存储
     */
    public String uploadFileToHuoShan(MultipartFile file) throws Exception {
        String accessKey  = resolve("VOLC_TOS_ACCESS_KEY",  accessKeyConfig,  "TOS AccessKey（volc.tos.access-key）");
        String secretKey  = resolve("VOLC_TOS_SECRET_KEY",  secretKeyConfig,  "TOS SecretKey（volc.tos.secret-key）");
        String bucketName = resolve("VOLC_TOS_BUCKET_NAME", bucketNameConfig, "TOS 桶名（volc.tos.bucket-name）");
        String endpoint   = resolveOptional("VOLC_TOS_ENDPOINT", endpointConfig);
        String region     = resolveOptional("VOLC_TOS_REGION",   regionConfig);

        TOSClientConfiguration configuration = TOSClientConfiguration.builder()
                .region(region)
                .endpoint(endpoint)
                .credentials(new StaticCredentials(accessKey, secretKey))
                .build();

        TOSV2 client = new TOSV2ClientBuilder().build(configuration);

        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String fileName = UUID.randomUUID().toString() + suffix;

            PutObjectInput input = new PutObjectInput()
                    .setBucket(bucketName)
                    .setKey(fileName)
                    .setContent(new ByteArrayInputStream(file.getBytes()));

            PutObjectOutput output = client.putObject(input);
            String preSignedUrl = client.preSignedURL(HttpMethod.GET, bucketName, fileName, Duration.ofHours(presignHours));

            System.out.println("TOS 上传成功：" + preSignedUrl);
            return preSignedUrl;
        } finally {
            if (client != null) {
                client.close();
            }
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

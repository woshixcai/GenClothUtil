package com.UiUtil.uitl;


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

    @Value("${volc.tos.access-key:}")
    private String accessKey;

    @Value("${volc.tos.secret-key:}")
    private String secretKey;

    @Value("${volc.tos.endpoint:tos-cn-beijing.volces.com}")
    private String endpoint;

    @Value("${volc.tos.region:cn-beijing}")
    private String region;

    @Value("${volc.tos.bucket-name:}")
    private String bucketName;

    @Value("${volc.tos.presign-hours:1}")
    private long presignHours;

    private static String AliyunfileToBase64(MultipartFile file) throws IOException {
        // 1. 获取文件MIME类型（如image/jpeg、image/png）
        String contentType = file.getContentType();
        if (contentType == null) {
            contentType = "image/jpeg"; // 默认JPG
        }
        // 2. 读取文件字节并编码为Base64
        byte[] fileBytes = file.getBytes();
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);
        // 4. 拼接带格式前缀的 Base64 字符串（qwen-vl 必须带前缀）
        return String.format("data:image/%s;base64,%s", contentType, base64Content);
    }

    /**
     * 普通File文件转MD5（32位小写）
     * @param file 本地文件/服务器文件
     * @return 32位MD5字符串
     * @throws IOException 文件读取异常
     */
    public static String fileToMd5(File file) throws IOException {
        // 1. 校验文件有效性
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件");
        }
        // 2. Hutool工具类直接生成MD5（无需手动处理流）
        return DigestUtil.md5Hex(file);
    }

    /**
     * MultipartFile（上传文件）转MD5（适配接口上传场景）
     * @param multipartFile 前端上传的文件
     * @return 32位MD5字符串
     * @throws IOException 文件读取异常
     */
    public static String multipartFileToMd5(MultipartFile multipartFile) throws IOException {
        // 1. 空文件校验
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        // 2. 读取字节流生成MD5
        return DigestUtil.md5Hex(multipartFile.getBytes());
    }
    /**
     * 辅助方法：上传MultipartFile到对象存储（示例，需根据你的OSS实现）
     * @param file 上传的图片文件
     * @return 图片的公开访问URL
     */
    public String uploadFileToHuoShan(MultipartFile file) throws Exception {
        if (accessKey == null || accessKey.isBlank() ||
                secretKey == null || secretKey.isBlank() ||
                bucketName == null || bucketName.isBlank()) {
            throw new RuntimeException("缺少TOS配置，请设置 volc.tos.access-key/secret-key/bucket-name");
        }

        // 1. 初始化 TOS 客户端（2.8.8 版本正确写法）
        // 1. 构建配置对象
        TOSClientConfiguration configuration = TOSClientConfiguration.builder()
                .region(region)
                .endpoint(endpoint)
                .credentials(new StaticCredentials(accessKey.trim(), secretKey.trim()))
                .build();

        // 2. 初始化客户端
        TOSV2 client = new TOSV2ClientBuilder().build(configuration);

        try {
            // 2. 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String suffix = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String fileName = UUID.randomUUID().toString() + suffix;

            // 3. 构建上传请求
            PutObjectInput input = new PutObjectInput()
                    .setBucket(bucketName)
                    .setKey(fileName)
                    .setContent(new ByteArrayInputStream(file.getBytes()));

            // 4. 执行上传
            PutObjectOutput output = client.putObject(input);
            //获取能访问的url
            String preSignedUrl = client.preSignedURL(HttpMethod.GET, bucketName, fileName, Duration.ofHours(presignHours));

            System.out.println("TOS 上传成功：" + preSignedUrl);
            return preSignedUrl;
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}

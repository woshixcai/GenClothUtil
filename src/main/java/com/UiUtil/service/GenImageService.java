package com.UiUtil.service;

/**
 * 图片生成服务：处理换装生图请求、查询任务状态，并对生成的结果图片进行 TOS 上传缓存，
 * 避免重复调用生图接口。
 */
import com.UiUtil.shared.result.HuoShanResult;
import com.UiUtil.entity.UploadImageCache;
import com.UiUtil.mapper.UploadImageCacheMapper;
import com.UiUtil.shared.util.GenIdUtils;
import com.UiUtil.shared.util.ImageUtils;
import com.UiUtil.shared.util.VlcengineUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GenImageService {


    @Autowired
    VlcengineUtils vlcengineUtils;

    @Autowired
    ImageUtils imageUtils;

    @Autowired
    UploadImageCacheMapper uploadImageCacheMapper;

    public HuoShanResult genImage(MultipartFile demoFile,
                                  MultipartFile closeFile,
                                  String text) {
        long totalStart = System.currentTimeMillis();
        try {
            // ── 1. MD5 去重查缓存 ──────────────────────────────────────────
            String demoMd5 = ImageUtils.multipartFileToMd5(demoFile);
            String closeMd5 = ImageUtils.multipartFileToMd5(closeFile);
            LambdaQueryWrapper<UploadImageCache> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(UploadImageCache::getImageMd5, demoMd5, closeMd5);
            List<UploadImageCache> cacheList = uploadImageCacheMapper.selectList(queryWrapper);
            Map<String, String> md5ToUrlMap = cacheList.stream()
                    .collect(Collectors.toMap(UploadImageCache::getImageMd5, UploadImageCache::getTosUrl));

            String demoFileUrl = md5ToUrlMap.get(demoMd5);
            String closeFileUrl = md5ToUrlMap.get(closeMd5);

            // ── 2. 上传到 TOS（未命中缓存才上传，记录上传耗时）────────────
            long uploadStart = System.currentTimeMillis();
            if (demoFileUrl == null) {
                demoFileUrl = imageUtils.uploadFileToHuoShan(demoFile);
                uploadImageCacheMapper.insert(new UploadImageCache(
                        GenIdUtils.getSnowflakeId(), demoMd5, demoFileUrl, new Date(), new Date(), null));
            }
            if (closeFileUrl == null) {
                closeFileUrl = imageUtils.uploadFileToHuoShan(closeFile);
                uploadImageCacheMapper.insert(new UploadImageCache(
                        GenIdUtils.getSnowflakeId(), closeMd5, closeFileUrl, new Date(), new Date(), null));
            }
            long uploadEnd = System.currentTimeMillis();

            // ── 3. 调用大模型生图（记录生图耗时）────────────────────────────
            long generateStart = System.currentTimeMillis();
            HuoShanResult result = vlcengineUtils.generateTryonImage(demoFileUrl, closeFileUrl, text);
            long generateEnd = System.currentTimeMillis();

            // ── 4. 汇总耗时写入结果 ──────────────────────────────────────────
            long totalEnd = System.currentTimeMillis();
            result.setUploadSecond(HuoShanResult.toSeconds(uploadEnd - uploadStart));
            result.setGenerateSecond(HuoShanResult.toSeconds(generateEnd - generateStart));
            result.setTotalSecond(HuoShanResult.toSeconds(totalEnd - totalStart));

            System.out.printf("[GenImageService] 上传耗时=%.1fs  生图耗时=%.1fs  总耗时=%.1fs%n",
                    result.getUploadSecond(), result.getGenerateSecond(), result.getTotalSecond());

            return result;

        } catch (IllegalArgumentException e) {
            throw new RuntimeException("图片MD5生成失败：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("图片上传TOS失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("生图接口调用失败：" + e.getMessage(), e);
        }
    }
}

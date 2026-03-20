package com.UiUtil.tryon.service;

import com.UiUtil.auth.service.QuotaService;
import com.UiUtil.auth.service.UsageLogService;
import com.UiUtil.entity.UploadImageCache;
import com.UiUtil.mapper.UploadImageCacheMapper;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.shared.result.HuoShanResult;
import com.UiUtil.shared.util.GenIdUtils;
import com.UiUtil.shared.util.ImageUtils;
import com.UiUtil.shared.util.VlcengineUtils;
import com.UiUtil.tryon.entity.TryonRecord;
import com.UiUtil.tryon.mapper.TryonRecordMapper;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TryonService {

    @Autowired QuotaService quotaService;
    @Autowired PreferenceService preferenceService;
    @Autowired UsageLogService usageLogService;
    @Autowired TryonRecordMapper recordMapper;
    @Autowired VlcengineUtils vlcengineUtils;
    @Autowired ImageUtils imageUtils;
    @Autowired UploadImageCacheMapper uploadImageCacheMapper;

    public Map<String, Object> recommend(List<MultipartFile> clothesFiles,
                                          List<MultipartFile> referenceFiles,
                                          String style, String scene, String season) {
        Map<String, Object> resp = new HashMap<>();
        UserContext.LoginUser user = UserContext.current();

        if (!quotaService.consumeQuota(user.getUserId())) {
            resp.put("recommendText", "今日生图次数已达上限，请联系管理员调整配额");
            resp.put("recommendImgs", Collections.emptyList());
            return resp;
        }

        MultipartFile clothes   = (clothesFiles   == null || clothesFiles.isEmpty())   ? null : clothesFiles.get(0);
        MultipartFile reference = (referenceFiles == null || referenceFiles.isEmpty()) ? null : referenceFiles.get(0);

        if (clothes == null || clothes.isEmpty()) {
            resp.put("recommendText", "请至少上传1张需要穿版的衣服图片");
            resp.put("recommendImgs", Collections.emptyList());
            return resp;
        }
        if (reference == null || reference.isEmpty()) {
            resp.put("recommendText", "请至少上传1张参考穿搭图片");
            resp.put("recommendImgs", Collections.emptyList());
            return resp;
        }

        String prefPrefix = preferenceService.buildPrefPromptPrefix(user.getUserId());
        String basePrompt = "请基于参考穿搭图与衣服图生成真实自然的试穿效果图。" +
                "要求：风格为" + safe(style) + "，适用场景为" + safe(scene) + "，季节为" + safe(season) + "。" +
                "保持人物比例自然、衣物材质与纹理清晰、光照一致、背景不过度抢眼。";
        String finalPrompt = prefPrefix + basePrompt;

        HuoShanResult result;
        try {
            result = genImageWithCache(reference, clothes, finalPrompt);
        } catch (Exception e) {
            result = new HuoShanResult();
            result.setSuccess(false);
            result.setErrorMsg(e.getMessage());
        }

        int tokenUsed = 0;
        usageLogService.record("recommend", tokenUsed);

        TryonRecord record = new TryonRecord();
        record.setUserId(user.getUserId());
        record.setShopId(user.getShopId());
        record.setResultUrls(result.isSuccess() ? JSON.toJSONString(result.getImageUrls()) : null);
        record.setPromptUsed(finalPrompt);
        record.setPrefSnapshot(prefPrefix.isEmpty() ? null : prefPrefix);
        record.setStyle(style);
        record.setScene(scene);
        record.setSeason(season);
        record.setTokenUsed(tokenUsed);
        record.setCreatedTime(new Date());
        recordMapper.insert(record);

        if (result.isSuccess()) {
            resp.put("recordId", record.getId());
            resp.put("recommendText", "已为你生成穿搭试衣效果图（风格：" + safe(style) +
                    "，场景：" + safe(scene) + "，季节：" + safe(season) + "）。");
            resp.put("recommendImgs", result.getImageUrls());
        } else {
            resp.put("recommendText", result.getErrorMsg() == null ? "生成失败，请稍后重试" : result.getErrorMsg());
            resp.put("recommendImgs", Collections.emptyList());
        }
        resp.put("uploadSecond",   result.getUploadSecond());
        resp.put("generateSecond", result.getGenerateSecond());
        resp.put("totalSecond",    result.getTotalSecond());
        return resp;
    }

    private HuoShanResult genImageWithCache(MultipartFile demoFile, MultipartFile closeFile, String prompt)
            throws Exception {
        long totalStart = System.currentTimeMillis();
        String demoMd5  = ImageUtils.multipartFileToMd5(demoFile);
        String closeMd5 = ImageUtils.multipartFileToMd5(closeFile);

        LambdaQueryWrapper<UploadImageCache> q = new LambdaQueryWrapper<>();
        q.in(UploadImageCache::getImageMd5, demoMd5, closeMd5);
        List<UploadImageCache> cacheList = uploadImageCacheMapper.selectList(q);
        Map<String, String> md5ToUrl = cacheList.stream()
                .collect(Collectors.toMap(UploadImageCache::getImageMd5, UploadImageCache::getTosUrl));

        String demoUrl  = md5ToUrl.get(demoMd5);
        String closeUrl = md5ToUrl.get(closeMd5);

        long uploadStart = System.currentTimeMillis();
        if (demoUrl == null) {
            demoUrl = imageUtils.uploadFileToHuoShan(demoFile);
            uploadImageCacheMapper.insert(new UploadImageCache(
                    GenIdUtils.getSnowflakeId(), demoMd5, demoUrl, new Date(), new Date(), null));
        }
        if (closeUrl == null) {
            closeUrl = imageUtils.uploadFileToHuoShan(closeFile);
            uploadImageCacheMapper.insert(new UploadImageCache(
                    GenIdUtils.getSnowflakeId(), closeMd5, closeUrl, new Date(), new Date(), null));
        }
        long uploadEnd = System.currentTimeMillis();

        long generateStart = System.currentTimeMillis();
        HuoShanResult result = vlcengineUtils.generateTryonImage(demoUrl, closeUrl, prompt);
        long generateEnd = System.currentTimeMillis();

        long totalEnd = System.currentTimeMillis();
        result.setUploadSecond(HuoShanResult.toSeconds(uploadEnd - uploadStart));
        result.setGenerateSecond(HuoShanResult.toSeconds(generateEnd - generateStart));
        result.setTotalSecond(HuoShanResult.toSeconds(totalEnd - totalStart));
        return result;
    }

    private static String safe(String v) {
        return (v == null || v.trim().isEmpty()) ? "未指定" : v.trim();
    }
}

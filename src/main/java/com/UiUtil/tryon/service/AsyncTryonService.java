package com.UiUtil.tryon.service;

import com.UiUtil.auth.service.UsageLogService;
import com.UiUtil.entity.UploadImageCache;
import com.UiUtil.mapper.UploadImageCacheMapper;
import com.UiUtil.shared.result.HuoShanResult;
import com.UiUtil.shared.util.GenIdUtils;
import com.UiUtil.shared.util.ImageUtils;
import com.UiUtil.shared.util.VlcengineUtils;
import com.UiUtil.tryon.entity.TryonRecord;
import com.UiUtil.tryon.mapper.TryonRecordMapper;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异步执行换装生图，必须是独立 Bean（Spring AOP 代理 @Async）。
 * 调用方在 HTTP 请求内完成 quota 扣减和 record 预插入，
 * 此 Bean 在后台线程完成上传+生图+更新 record。
 */
@Service
public class AsyncTryonService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTryonService.class);

    // status 常量
    public static final int STATUS_PENDING = 2;   // 等待/生成中
    public static final int STATUS_SUCCESS = 1;   // 成功
    public static final int STATUS_FAILED  = 0;   // 失败

    @Autowired VlcengineUtils         vlcengineUtils;
    @Autowired ImageUtils             imageUtils;
    @Autowired TryonRecordMapper      recordMapper;
    @Autowired UploadImageCacheMapper uploadImageCacheMapper;
    @Autowired UsageLogService        usageLogService;

    /**
     * @param taskId       预先插入的 TryonRecord ID
     * @param clothBytes   衣服图字节（请求线程内预读）
     * @param clothName    原始文件名
     * @param refBytes     参考图字节
     * @param refName      原始文件名
     * @param finalPrompt  已拼好的完整提示词
     */
    @Async
    public void execute(Long taskId,
                        byte[] clothBytes, String clothName,
                        byte[] refBytes,   String refName,
                        String finalPrompt,
                        String style, String scene, String season) {
        try {
            // 1. 上传（含 MD5 缓存去重）
            String clothUrl = uploadWithCache(clothBytes, clothName);
            String refUrl   = uploadWithCache(refBytes,  refName);

            // 2. 生图
            HuoShanResult result = vlcengineUtils.generateTryonImage(refUrl, clothUrl, finalPrompt);

            // 3. 更新 record
            TryonRecord upd = new TryonRecord();
            upd.setId(taskId);
            upd.setStatus(result.isSuccess() ? STATUS_SUCCESS : STATUS_FAILED);
            upd.setResultUrls(result.isSuccess()
                    ? JSON.toJSONString(result.getImageUrls()) : null);
            upd.setUploadSecond(result.getUploadSecond()   != null ? String.valueOf(result.getUploadSecond())   : null);
            upd.setGenerateSecond(result.getGenerateSecond() != null ? String.valueOf(result.getGenerateSecond()) : null);
            upd.setTotalSecond(result.getTotalSecond()     != null ? String.valueOf(result.getTotalSecond())     : null);
            recordMapper.updateById(upd);

            usageLogService.record("recommend", 0);
            log.info("异步换装完成 taskId={} success={}", taskId, result.isSuccess());

        } catch (Exception e) {
            log.error("异步换装失败 taskId={}", taskId, e);
            TryonRecord upd = new TryonRecord();
            upd.setId(taskId);
            upd.setStatus(STATUS_FAILED);
            upd.setResultUrls(null);
            recordMapper.updateById(upd);
        }
    }

    /**
     * 上传图片并缓存对象 Key；每次调用都生成新鲜预签名 URL 返回给调用方。
     * 数据库只存 TOS 对象 Key，避免存储过期的预签名 URL。
     */
    private String uploadWithCache(byte[] bytes, String filename) throws Exception {
        String md5 = cn.hutool.crypto.digest.DigestUtil.md5Hex(bytes);
        LambdaQueryWrapper<UploadImageCache> q = new LambdaQueryWrapper<>();
        q.eq(UploadImageCache::getImageMd5, md5);
        UploadImageCache cached = uploadImageCacheMapper.selectOne(q);
        if (cached != null) {
            // 缓存命中：返回公开 URL（兼容历史存储的 Key/过期预签名 URL）
            return imageUtils.toPublicUrl(cached.getTosUrl());
        }
        // uploadBytesToHuoShan 现在返回公开 URL
        String urlOrKey = imageUtils.uploadBytesToHuoShan(bytes, filename);
        uploadImageCacheMapper.insert(new UploadImageCache(
                GenIdUtils.getSnowflakeId(), md5, urlOrKey, new Date(), new Date(), null));
        return imageUtils.toPublicUrl(urlOrKey);
    }
}

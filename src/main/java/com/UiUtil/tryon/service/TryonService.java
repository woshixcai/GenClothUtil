package com.UiUtil.tryon.service;

/**
 * 换装生图主服务：校验每日配额与 AI 使用权限，读取用户偏好构建负面提示词，
 * 创建任务记录后提交异步生图任务，并提供任务状态查询和历史记录接口。
 */
import com.UiUtil.auth.service.QuotaService;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.tryon.entity.TryonRecord;
import com.UiUtil.tryon.mapper.TryonRecordMapper;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class TryonService {

    @Autowired QuotaService      quotaService;
    @Autowired PreferenceService preferenceService;
    @Autowired TryonRecordMapper recordMapper;
    @Autowired AsyncTryonService asyncTryonService;

    /**
     * 提交异步换装任务。
     * 立即返回 taskId（即 TryonRecord.id），前端轮询 /tryon/result/{taskId} 获取结果。
     */
    public Map<String, Object> submitAsync(List<MultipartFile> clothesFiles,
                                            List<MultipartFile> referenceFiles,
                                            String style, String scene, String season)
            throws Exception {
        Map<String, Object> resp = new HashMap<>();
        UserContext.LoginUser user = UserContext.current();

        // 1. 校验 AI 生图权限 + 配额
        quotaService.checkCanUseAi(user.getUserId());
        if (!quotaService.consumeQuota(user.getUserId())) {
            resp.put("error", "今日生图次数已达上限，请联系管理员调整配额");
            return resp;
        }

        // 2. 校验文件
        MultipartFile clothes   = (clothesFiles   == null || clothesFiles.isEmpty())   ? null : clothesFiles.get(0);
        MultipartFile reference = (referenceFiles == null || referenceFiles.isEmpty()) ? null : referenceFiles.get(0);
        if (clothes == null || clothes.isEmpty()) {
            resp.put("error", "请至少上传1张需要穿版的衣服图片"); return resp;
        }
        if (reference == null || reference.isEmpty()) {
            resp.put("error", "请至少上传1张参考穿搭图片"); return resp;
        }

        // 3. 预读字节（MultipartFile 在请求结束后不可用）
        byte[] clothBytes = clothes.getBytes();
        byte[] refBytes   = reference.getBytes();
        String clothName  = clothes.getOriginalFilename();
        String refName    = reference.getOriginalFilename();

        // 4. 构建 prompt
        String prefPrefix = preferenceService.buildPrefPromptPrefix(user.getUserId());
        String finalPrompt = prefPrefix +
                "请基于参考穿搭图与衣服图生成真实自然的试穿效果图。" +
                "要求：风格为" + safe(style) + "，适用场景为" + safe(scene) + "，季节为" + safe(season) + "。" +
                "保持人物比例自然、衣物材质与纹理清晰、光照一致、背景不过度抢眼。";

        // 5. 插入 pending 记录
        TryonRecord record = new TryonRecord();
        record.setUserId(user.getUserId());
        record.setShopId(user.getShopId());
        record.setPromptUsed(finalPrompt);
        record.setPrefSnapshot(prefPrefix.isEmpty() ? null : prefPrefix);
        record.setStyle(style);
        record.setScene(scene);
        record.setSeason(season);
        record.setTokenUsed(0);
        record.setCreatedTime(new Date());
        record.setStatus(AsyncTryonService.STATUS_PENDING);  // 2 = 生成中
        recordMapper.insert(record);

        // 6. 异步派发（不阻塞 HTTP 响应）
        asyncTryonService.execute(record.getId(),
                clothBytes, clothName, refBytes, refName,
                finalPrompt, style, scene, season);

        resp.put("taskId", record.getId());
        return resp;
    }

    /**
     * 轮询任务结果。
     * status: "pending" | "done" | "failed"
     */
    public Map<String, Object> getResult(Long taskId) {
        Map<String, Object> resp = new HashMap<>();
        UserContext.LoginUser user = UserContext.current();

        TryonRecord record = recordMapper.selectById(taskId);
        if (record == null || !record.getUserId().equals(user.getUserId())) {
            resp.put("status", "failed");
            resp.put("message", "任务不存在");
            return resp;
        }

        int status = record.getStatus() == null ? AsyncTryonService.STATUS_PENDING : record.getStatus();

        if (status == AsyncTryonService.STATUS_PENDING) {
            resp.put("status", "pending");
            return resp;
        }

        if (status == AsyncTryonService.STATUS_SUCCESS) {
            List<String> imgs = record.getResultUrls() != null
                    ? JSON.parseArray(record.getResultUrls(), String.class)
                    : Collections.emptyList();
            resp.put("status",         "done");
            resp.put("recordId",       record.getId());
            resp.put("recommendImgs",  imgs);
            resp.put("recommendText",  "已为你生成穿搭试衣效果图（风格：" + safe(record.getStyle()) +
                    "，场景：" + safe(record.getScene()) + "，季节：" + safe(record.getSeason()) + "）。");
            resp.put("uploadSecond",   record.getUploadSecond());
            resp.put("generateSecond", record.getGenerateSecond());
            resp.put("totalSecond",    record.getTotalSecond());
            return resp;
        }

        resp.put("status",  "failed");
        resp.put("message", "生成失败，请重新尝试");
        return resp;
    }

    private static String safe(String v) {
        return (v == null || v.trim().isEmpty()) ? "未指定" : v.trim();
    }
}

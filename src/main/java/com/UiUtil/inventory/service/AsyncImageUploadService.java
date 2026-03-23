package com.UiUtil.inventory.service;

/**
 * 异步图片上传服务：在商品入库后台将图片上传至火山引擎 TOS，并将图片记录写入 cloth_image 表。
 */
import com.UiUtil.inventory.entity.ClothImage;
import com.UiUtil.inventory.mapper.ClothImageMapper;
import com.UiUtil.shared.util.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 异步图片上传服务。
 * 必须是独立 Bean（不能和调用方在同一个类），Spring AOP 代理才能拦截 @Async。
 */
@Service
public class AsyncImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(AsyncImageUploadService.class);

    @Autowired ImageUtils       imageUtils;
    @Autowired ClothImageMapper imageMapper;

    /**
     * 异步上传图片字节到 TOS，成功后写入 cloth_image 记录。
     * 调用方先插入商品主记录，再调用此方法，用户无需等待上传完成。
     *
     * @param itemId   商品 ID
     * @param bytes    图片字节（MultipartFile 需在请求结束前预读）
     * @param filename 原始文件名，用于确定扩展名
     */
    @Async
    public void uploadAndLink(Long itemId, byte[] bytes, String filename) {
        try {
            String tosUrl = imageUtils.uploadBytesToHuoShan(bytes, filename);

            ClothImage img = new ClothImage();
            img.setItemId(itemId);
            img.setTosUrl(tosUrl);
            img.setIsMain(1);
            img.setSortOrder(0);
            img.setCreatedTime(new Date());
            imageMapper.insert(img);

            log.info("异步上传完成 itemId={} tosUrl={}", itemId, tosUrl);
        } catch (Exception e) {
            log.error("异步上传失败 itemId={}", itemId, e);
        }
    }
}

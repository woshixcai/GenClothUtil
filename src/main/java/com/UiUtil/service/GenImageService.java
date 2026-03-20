package com.UiUtil.service;

import com.UiUtil.Result.HuoShanResult;
import com.UiUtil.entity.UploadImageCache;
import com.UiUtil.mapper.UploadImageCacheMapper;
import com.UiUtil.uitl.GenIdUtils;
import com.UiUtil.uitl.ImageUtils;
import com.UiUtil.uitl.VlcengineUtils;
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
                                  MultipartFile closeFile,// 接收前端上传的图片文件
                                  String text) {
        try {
            String demoMd5 = ImageUtils.multipartFileToMd5(demoFile);
            String closeMd5 = ImageUtils.multipartFileToMd5(closeFile);
            LambdaQueryWrapper<UploadImageCache> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(UploadImageCache::getImageMd5, demoMd5, closeMd5);
            List<UploadImageCache> cacheList = uploadImageCacheMapper.selectList(queryWrapper);

            Map<String, String> md5ToUrlMap = cacheList.stream()
                    .collect(Collectors.toMap(UploadImageCache::getImageMd5, UploadImageCache::getTosUrl));

            String demoFileUrl = md5ToUrlMap.get(demoMd5);
            String closeFileUrl = md5ToUrlMap.get(closeMd5);

            if (demoFileUrl == null ) {
                demoFileUrl = imageUtils.uploadFileToHuoShan(demoFile);
                uploadImageCacheMapper.insert(new UploadImageCache(GenIdUtils.getSnowflakeId(),demoMd5,demoFileUrl,new Date(),new Date(),null));
            }
            if (closeFileUrl == null) {
                closeFileUrl = imageUtils.uploadFileToHuoShan(closeFile);
                uploadImageCacheMapper.insert(new UploadImageCache(GenIdUtils.getSnowflakeId(),closeMd5,closeFileUrl,new Date(),new Date(),null));
            }
            HuoShanResult huoShanResult = vlcengineUtils.generateTryonImage(demoFileUrl, closeFileUrl, text);
            return huoShanResult;

        } catch (IllegalArgumentException e) {
            throw new RuntimeException("图片MD5生成失败：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("图片上传TOS失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("生图接口调用失败：" + e.getMessage(), e);
        }
    }
}

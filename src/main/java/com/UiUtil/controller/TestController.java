package com.UiUtil.controller;

import com.UiUtil.Result.HuoShanResult;
import com.UiUtil.service.GenImageService;
import com.UiUtil.uitl.AliyunUtils;
import com.UiUtil.uitl.ImageUtils;
import com.UiUtil.uitl.VlcengineUtils;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("TestController")
public class TestController {

    @Autowired
    GenImageService genImageService;

    @Autowired
    AliyunUtils aliyunUtils;


    @GetMapping("test")
    public String test() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "hello world";
    }

    /**
     * 给静态页面用的推荐接口：接收多张图，但当前只取第一张衣服图 + 第一张参考图做生成。
     * 返回结构与前端`main.js`一致：{ recommendText, recommendImgs }。
     */
    @PostMapping("recommend")
    public Map<String, Object> recommend(@RequestParam(value = "clothesFiles", required = false) List<MultipartFile> clothesFiles,
                                         @RequestParam(value = "referenceFiles", required = false) List<MultipartFile> referenceFiles,
                                         @RequestParam(value = "style", required = false) String style,
                                         @RequestParam(value = "scene", required = false) String scene,
                                         @RequestParam(value = "season", required = false) String season) {
        Map<String, Object> resp = new HashMap<>();

        MultipartFile clothes = (clothesFiles == null || clothesFiles.isEmpty()) ? null : clothesFiles.get(0);
        MultipartFile reference = (referenceFiles == null || referenceFiles.isEmpty()) ? null : referenceFiles.get(0);
        if (clothes == null || clothes.isEmpty()) {
            resp.put("recommendText", "请至少上传1张需要穿版的衣服图片");
            resp.put("recommendImgs", List.of());
            return resp;
        }
        if (reference == null || reference.isEmpty()) {
            resp.put("recommendText", "请至少上传1张参考穿搭图片");
            resp.put("recommendImgs", List.of());
            return resp;
        }

        String prompt = buildPrompt(style, scene, season);
        HuoShanResult result = genImageService.genImage(reference, clothes, prompt);

        if (result.isSuccess()) {
            resp.put("recommendText", "已为你生成穿搭试衣效果图（风格：" + safe(style) + "，场景：" + safe(scene) + "，季节：" + safe(season) + "）。");
            resp.put("recommendImgs", result.getImageUrls());
        } else {
            resp.put("recommendText", result.getErrorMsg() == null ? "生成失败，请稍后重试" : result.getErrorMsg());
            resp.put("recommendImgs", List.of());
        }
        return resp;
    }

    private static String buildPrompt(String style, String scene, String season) {
        // 这里的prompt是给生图模型的，先用一个稳定的中文模板，后续可再迭代成更精细的提示词。
        return "请基于参考穿搭图与衣服图生成真实自然的试穿效果图。" +
                "要求：风格为" + safe(style) + "，适用场景为" + safe(scene) + "，季节为" + safe(season) + "。" +
                "保持人物比例自然、衣物材质与纹理清晰、光照一致、背景不过度抢眼。";
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "未指定" : v.trim();
    }

    @PostMapping("testAliyunDemo")
    public String testAliyunDemo(@RequestParam("file") MultipartFile file, // 接收前端上传的图片文件
                                 @RequestParam("text") String text) {
        try {
            return aliyunUtils.qWenVLPlus(file, text);
            //return AliyunUtils.initCollection().toString();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @PostMapping("testDyDemo")
    public HuoShanResult testDyDemo(@RequestParam("demoFile") MultipartFile demoFile,
                                    @RequestParam("closeFile") MultipartFile closeFile,// 接收前端上传的图片文件
                                    @RequestParam("text") String text) {
        long before = System.currentTimeMillis();
        HuoShanResult huoShanResult = genImageService.genImage(demoFile, closeFile, text);
        long after = System.currentTimeMillis();
        double costSeconds = Math.round((after - before) / 1000.0 * 10) / 10.0;
        huoShanResult.setSecond(costSeconds);
        return huoShanResult;
    }

}

package com.UiUtil.inventory.service;

import com.UiUtil.inventory.entity.*;
import com.UiUtil.inventory.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.shared.util.AliyunUtils;
import com.UiUtil.shared.util.ImageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    @Autowired AliyunUtils             aliyunUtils;
    @Autowired ImageUtils              imageUtils;
    @Autowired ClothItemMapper         itemMapper;
    @Autowired ClothImageMapper        imageMapper;
    @Autowired ClothSkuMapper          skuMapper;
    @Autowired AsyncImageUploadService asyncUpload;

    // 商品编号生成：yyyyMMddHHmmss + 3位序号，同一秒内最多999个
    private static final AtomicInteger SEQ      = new AtomicInteger(0);
    private static volatile String     SEQ_MARK = "";

    private static String genItemNo() {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        synchronized (InventoryService.class) {
            if (!ts.equals(SEQ_MARK)) { SEQ_MARK = ts; SEQ.set(0); }
            return ts + String.format("%03d", SEQ.incrementAndGet());
        }
    }

    private static final String AI_NAME_PROMPT =
            "识别图中服装/配饰，严格按格式返回，不输出其他内容：\n" +
            "名称：(颜色+款式，10字内)\n" +
            "描述：(版型/材质/风格，30字内)\n" +
            "分类：(上装/下装/外套/裙装/配饰 选一个)";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryService.class);

    /**
     * AI 识别商品名称/描述/分类，纯识别，不涉及 TOS 上传。
     */
    public Map<String, String> aiRecognize(MultipartFile image) throws Exception {
        long t0 = System.currentTimeMillis();
        String raw;
        try {
            raw = aliyunUtils.qWenVLPlus(image, AI_NAME_PROMPT);
            log.info("[AI识别] 耗时 {}ms，图片大小 {}KB，返回：{}",
                    System.currentTimeMillis() - t0,
                    image.getSize() / 1024,
                    raw.replace("\n", " | "));
        } catch (Exception e) {
            log.error("[AI识别] 耗时 {}ms 后失败，图片大小 {}KB，错误：{}",
                    System.currentTimeMillis() - t0,
                    image.getSize() / 1024,
                    e.getMessage());
            throw e;
        }
        Map<String, String> result = new HashMap<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("名称：") || trimmed.startsWith("名称:")) {
                result.put("name", trimmed.replaceFirst("名称[：:]\\s*", "").trim());
            } else if (trimmed.startsWith("描述：") || trimmed.startsWith("描述:")) {
                result.put("description", trimmed.replaceFirst("描述[：:]\\s*", "").trim());
            } else if (trimmed.startsWith("分类：") || trimmed.startsWith("分类:")) {
                result.put("category", trimmed.replaceFirst("分类[：:]\\s*", "").trim());
            }
        }
        if (!result.containsKey("name"))        result.put("name", "未识别商品");
        if (!result.containsKey("description")) result.put("description", "");
        if (!result.containsKey("category"))    result.put("category", "");
        return result;
    }

    /**
     * 写商品记录，图片异步上传到 TOS，入库立即返回。
     */
    public Long saveItem(MultipartFile mainImage,
                          String itemName,
                          String category,
                          BigDecimal costPrice,
                          BigDecimal salePrice,
                          String description,
                          String color,
                          String size,
                          Integer stockQty) throws Exception {
        UserContext.LoginUser user = UserContext.current();

        Long finalShopId = user.getShopId();
        if (finalShopId == null) {
            throw new RuntimeException("当前账号未绑定店铺，无法入库，请联系超级管理员");
        }

        ClothItem item = new ClothItem();
        item.setShopId(finalShopId);
        item.setItemNo(genItemNo());
        item.setItemName(itemName);
        item.setCategory(category);
        item.setCostPrice(costPrice);
        item.setSalePrice(salePrice);
        item.setDescription(description);
        item.setStatus(1);
        item.setCreatedBy(user.getUserId());
        item.setCreatedTime(new Date());
        itemMapper.insert(item);

        if (mainImage != null && !mainImage.isEmpty()) {
            byte[] bytes    = mainImage.getBytes();
            String filename = mainImage.getOriginalFilename();
            asyncUpload.uploadAndLink(item.getId(), bytes, filename);
        }

        if (color != null || size != null) {
            ClothSku sku = new ClothSku();
            sku.setItemId(item.getId());
            sku.setColor(color);
            sku.setSize(size);
            sku.setStockQty(stockQty == null ? 0 : stockQty);
            skuMapper.insert(sku);
        }

        return item.getId();
    }

    public List<ClothItem> listItems(String category, Integer status) {
        UserContext.LoginUser user = UserContext.current();
        LambdaQueryWrapper<ClothItem> q = new LambdaQueryWrapper<ClothItem>()
                .eq(ClothItem::getShopId, user.getShopId())
                .eq(ClothItem::getIsDeleted, 0)
                .orderByDesc(ClothItem::getCreatedTime);
        if (category != null && !category.isEmpty()) q.eq(ClothItem::getCategory, category);
        if (status != null) q.eq(ClothItem::getStatus, status);
        List<ClothItem> items = itemMapper.selectList(q);

        // 批量查主图：cloth_image.tos_url 可能是对象 Key / 旧的预签名 URL / 新的公开 URL
        // 统一转换成可长期访问的公开 URL（前提：Bucket 已开启公开读）
        if (!items.isEmpty()) {
            List<Long> ids = items.stream().map(ClothItem::getId).collect(Collectors.toList());
            List<ClothImage> images = imageMapper.selectMainByItemIds(ids);
            java.util.Map<Long, String> urlMap = new java.util.LinkedHashMap<>();
            for (ClothImage img : images) {
                urlMap.putIfAbsent(img.getItemId(), img.getTosUrl());
            }
            items.forEach(item -> item.setMainImageUrl(
                    imageUtils.toPublicUrl(urlMap.get(item.getId()))
            ));
        }

        if (user.getCanSeeCost() == null || user.getCanSeeCost() == 0) {
            items.forEach(i -> i.setCostPrice(null));
        }
        return items;
    }

    public void updateStock(Long skuId, int delta) {
        ClothSku sku = skuMapper.selectById(skuId);
        if (sku == null) throw new RuntimeException("SKU不存在");
        sku.setStockQty(Math.max(0, sku.getStockQty() + delta));
        skuMapper.updateById(sku);
    }

    public List<ClothSku> listSkusByItem(Long itemId) {
        return skuMapper.selectList(
                new LambdaQueryWrapper<ClothSku>().eq(ClothSku::getItemId, itemId));
    }

    public void setItemStatus(Long itemId, int status) {
        ClothItem update = new ClothItem();
        update.setId(itemId);
        update.setStatus(status);
        itemMapper.updateById(update);
    }

    /** 软删除商品 */
    public void deleteItem(Long itemId) {
        UserContext.LoginUser user = UserContext.current();
        ClothItem item = itemMapper.selectById(itemId);
        if (item == null) throw new RuntimeException("商品不存在");
        if (!item.getShopId().equals(user.getShopId())) throw new RuntimeException("无权删除该商品");
        ClothItem upd = new ClothItem();
        upd.setId(itemId);
        upd.setIsDeleted(1);
        itemMapper.updateById(upd);
    }
}

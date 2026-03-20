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
import java.util.*;

@Service
public class InventoryService {

    @Autowired AliyunUtils aliyunUtils;
    @Autowired ImageUtils imageUtils;
    @Autowired ClothItemMapper itemMapper;
    @Autowired ClothImageMapper imageMapper;
    @Autowired ClothSkuMapper skuMapper;

    private static final String AI_NAME_PROMPT =
            "请识别这件服装，用10字以内给出一个简洁的商品名称，格式为「颜色+款式」，" +
            "例如「米白色宽松休闲卫衣」。同时用一句话（50字以内）描述这件衣服的特点。" +
            "按以下格式返回，不要其他内容：\n名称：xxx\n描述：xxx";

    public Map<String, String> aiRecognize(MultipartFile image) throws Exception {
        String raw = aliyunUtils.qWenVLPlus(image, AI_NAME_PROMPT);
        Map<String, String> result = new HashMap<>();
        for (String line : raw.split("\n")) {
            if (line.startsWith("名称：") || line.startsWith("名称:")) {
                result.put("name", line.replaceFirst("名称[：:]", "").trim());
            } else if (line.startsWith("描述：") || line.startsWith("描述:")) {
                result.put("description", line.replaceFirst("描述[：:]", "").trim());
            }
        }
        if (!result.containsKey("name")) result.put("name", "未识别商品");
        if (!result.containsKey("description")) result.put("description", "");
        return result;
    }

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

        String tosUrl = imageUtils.uploadFileToHuoShan(mainImage);

        ClothItem item = new ClothItem();
        item.setShopId(user.getShopId());
        item.setItemName(itemName);
        item.setCategory(category);
        item.setCostPrice(costPrice);
        item.setSalePrice(salePrice);
        item.setDescription(description);
        item.setStatus(1);
        item.setCreatedBy(user.getUserId());
        item.setCreatedTime(new Date());
        itemMapper.insert(item);

        ClothImage img = new ClothImage();
        img.setItemId(item.getId());
        img.setTosUrl(tosUrl);
        img.setIsMain(1);
        img.setSortOrder(0);
        img.setCreatedTime(new Date());
        imageMapper.insert(img);

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
                .eq(ClothItem::getIsDeleted, 0);
        if (category != null && !category.isEmpty()) q.eq(ClothItem::getCategory, category);
        if (status != null) q.eq(ClothItem::getStatus, status);
        List<ClothItem> items = itemMapper.selectList(q);

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

    public void setItemStatus(Long itemId, int status) {
        ClothItem update = new ClothItem();
        update.setId(itemId);
        update.setStatus(status);
        itemMapper.updateById(update);
    }
}

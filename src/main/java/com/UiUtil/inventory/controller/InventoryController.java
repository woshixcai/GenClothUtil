package com.UiUtil.inventory.controller;

import com.UiUtil.inventory.entity.ClothItem;
import com.UiUtil.inventory.service.InventoryService;
import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired InventoryService inventoryService;

    @RequirePermission("inventory:manage")
    @PostMapping("/recognize")
    public ApiResult<Map<String, String>> recognize(@RequestParam("image") MultipartFile image) throws Exception {
        return ApiResult.ok(inventoryService.aiRecognize(image));
    }

    @RequirePermission("inventory:manage")
    @PostMapping("/items")
    public ApiResult<Long> addItem(
            @RequestParam("image") MultipartFile mainImage,
            @RequestParam String itemName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal costPrice,
            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) Integer stockQty) throws Exception {
        Long itemId = inventoryService.saveItem(mainImage, itemName, category,
                costPrice, salePrice, description, color, size, stockQty);
        return ApiResult.ok(itemId);
    }

    @RequirePermission("inventory:manage")
    @GetMapping("/items")
    public ApiResult<List<ClothItem>> listItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(inventoryService.listItems(category, status));
    }

    @RequirePermission("inventory:manage")
    @PutMapping("/skus/{skuId}/stock")
    public ApiResult<Void> updateStock(@PathVariable Long skuId,
                                        @RequestParam int delta) {
        inventoryService.updateStock(skuId, delta);
        return ApiResult.ok();
    }

    @RequirePermission("user:shop_manage")
    @PutMapping("/items/{itemId}/status")
    public ApiResult<Void> setStatus(@PathVariable Long itemId,
                                      @RequestParam int status) {
        inventoryService.setItemStatus(itemId, status);
        return ApiResult.ok();
    }
}

package com.UiUtil.inventory.controller;

/**
 * 服装库存接口：支持商品入库、列表查询、删除，以及 AI 图片识别和进货单（小票）一键导入。
 */
import com.UiUtil.inventory.dto.BatchAddStockRequest;
import com.UiUtil.inventory.dto.BatchItemIdsRequest;
import com.UiUtil.inventory.entity.ClothItem;
import com.UiUtil.inventory.entity.ClothSku;
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
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(inventoryService.listItems(category, status, keyword));
    }

    @RequirePermission("inventory:manage")
    @DeleteMapping("/items/{itemId}")
    public ApiResult<Void> deleteItem(@PathVariable Long itemId) {
        inventoryService.deleteItem(itemId);
        return ApiResult.ok();
    }

    @RequirePermission("inventory:manage")
    @PostMapping("/items/batch-delete")
    public ApiResult<Void> batchDeleteItems(@RequestBody BatchItemIdsRequest body) {
        if (body == null || body.getItemIds() == null) {
            return ApiResult.fail("itemIds 不能为空");
        }
        inventoryService.deleteItemsBatch(body.getItemIds());
        return ApiResult.ok();
    }

    @RequirePermission("inventory:manage")
    @PostMapping("/items/batch-add-stock")
    public ApiResult<Void> batchAddStock(@RequestBody BatchAddStockRequest body) {
        if (body == null || body.getItemIds() == null || body.getItemIds().isEmpty()) {
            return ApiResult.fail("itemIds 不能为空");
        }
        if (body.getDelta() == null) {
            return ApiResult.fail("delta 不能为空");
        }
        inventoryService.batchAddStockForItems(body.getItemIds(), body.getDelta());
        return ApiResult.ok();
    }

    @RequirePermission("inventory:manage")
    @GetMapping("/items/{itemId}/skus")
    public ApiResult<List<ClothSku>> listSkus(@PathVariable Long itemId) {
        return ApiResult.ok(inventoryService.listSkusByItem(itemId));
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

    // ────────────────────────────────────────────────
    // 进货单/小票：识别 → 入库（按图片识别）
    // ────────────────────────────────────────────────

    @RequirePermission("inventory:manage")
    @PostMapping("/receipt/recognize")
    public ApiResult<Map<String, Object>> recognizeReceipt(@RequestParam("image") MultipartFile image) throws Exception {
        return ApiResult.ok(inventoryService.parseReceipt(image));
    }

    @RequirePermission("inventory:manage")
    @PostMapping("/receipt/import")
    public ApiResult<Map<String, Object>> importReceipt(@RequestParam("image") MultipartFile image) throws Exception {
        return ApiResult.ok(inventoryService.importReceipt(image));
    }
}

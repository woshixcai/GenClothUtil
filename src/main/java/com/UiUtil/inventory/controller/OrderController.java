package com.UiUtil.inventory.controller;

import com.UiUtil.inventory.entity.ClothOrder;
import com.UiUtil.inventory.service.OrderService;
import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired OrderService orderService;

    /** 创建销售订单，body: { items:[{skuId,qty,unitPrice}], remark } */
    @RequirePermission("inventory:manage")
    @PostMapping
    public ApiResult<ClothOrder> create(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        String remark = (String) body.get("remark");
        return ApiResult.ok(orderService.createOrder(items, remark));
    }

    /** 订单列表 */
    @RequirePermission("inventory:manage")
    @GetMapping
    public ApiResult<List<ClothOrder>> list(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResult.ok(orderService.listOrders(startDate, endDate));
    }

    /** 整单退货 */
    @RequirePermission("inventory:manage")
    @PostMapping("/{orderId}/refund")
    public ApiResult<ClothOrder> refund(@PathVariable Long orderId,
                                         @RequestParam(required = false) String remark) {
        return ApiResult.ok(orderService.refundOrder(orderId, remark));
    }

    /** 销售报表 */
    @RequirePermission("inventory:manage")
    @GetMapping("/report/sales")
    public ApiResult<Map<String, Object>> salesReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResult.ok(orderService.salesReport(startDate, endDate));
    }
}

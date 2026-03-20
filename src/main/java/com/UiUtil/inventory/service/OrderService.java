package com.UiUtil.inventory.service;

import com.UiUtil.inventory.entity.*;
import com.UiUtil.inventory.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired ClothOrderMapper     orderMapper;
    @Autowired ClothOrderItemMapper orderItemMapper;
    @Autowired ClothItemMapper      itemMapper;
    @Autowired ClothSkuMapper       skuMapper;

    private static final AtomicInteger ORDER_SEQ  = new AtomicInteger(0);
    private static volatile String     ORDER_MARK = "";

    private static String genOrderNo() {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        synchronized (OrderService.class) {
            if (!ts.equals(ORDER_MARK)) { ORDER_MARK = ts; ORDER_SEQ.set(0); }
            return "SO" + ts + String.format("%03d", ORDER_SEQ.incrementAndGet());
        }
    }

    /**
     * 创建销售订单。
     * items 格式：[{skuId, qty, unitPrice}, ...]
     */
    @Transactional
    public ClothOrder createOrder(List<Map<String, Object>> items, String remark) {
        if (items == null || items.isEmpty()) throw new RuntimeException("订单明细不能为空");
        UserContext.LoginUser user = UserContext.current();

        ClothOrder order = new ClothOrder();
        order.setShopId(user.getShopId());
        order.setOrderNo(genOrderNo());
        order.setOrderType(1);
        order.setRemark(remark);
        order.setOperatorId(user.getUserId());
        order.setCreatedTime(new Date());

        BigDecimal total = BigDecimal.ZERO;
        List<ClothOrderItem> orderItems = new ArrayList<>();

        for (Map<String, Object> line : items) {
            Long skuId = toLong(line.get("skuId"));
            int  qty   = toInt(line.get("qty"));
            BigDecimal unitPrice = toBD(line.get("unitPrice"));

            ClothSku sku = skuMapper.selectById(skuId);
            if (sku == null) throw new RuntimeException("SKU " + skuId + " 不存在");
            if (sku.getStockQty() < qty) {
                throw new RuntimeException("库存不足（SKU " + skuId + " 当前库存 " + sku.getStockQty() + "）");
            }
            ClothItem item = itemMapper.selectById(sku.getItemId());
            if (item == null) throw new RuntimeException("商品不存在");

            // 扣减库存
            sku.setStockQty(sku.getStockQty() - qty);
            skuMapper.updateById(sku);

            BigDecimal price = unitPrice != null ? unitPrice :
                    (item.getSalePrice() != null ? item.getSalePrice() : BigDecimal.ZERO);
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));

            ClothOrderItem oi = new ClothOrderItem();
            oi.setItemId(item.getId());
            oi.setSkuId(skuId);
            oi.setItemName(item.getItemName());
            oi.setSkuDesc(buildSkuDesc(sku));
            oi.setQty(qty);
            oi.setUnitPrice(price);
            if (user.getCanSeeCost() != null && user.getCanSeeCost() == 1) {
                oi.setCostPrice(item.getCostPrice());
            }
            orderItems.add(oi);
        }

        order.setTotalAmount(total);
        orderMapper.insert(order);

        for (ClothOrderItem oi : orderItems) {
            oi.setOrderId(order.getId());
            orderItemMapper.insert(oi);
        }
        order.setItems(orderItems);
        return order;
    }

    /**
     * 整单退货：库存加回，创建退货单。
     */
    @Transactional
    public ClothOrder refundOrder(Long origOrderId, String remark) {
        ClothOrder orig = orderMapper.selectById(origOrderId);
        if (orig == null) throw new RuntimeException("原订单不存在");
        if (orig.getOrderType() == 2) throw new RuntimeException("退货单不能再退货");

        UserContext.LoginUser user = UserContext.current();
        if (!orig.getShopId().equals(user.getShopId())) throw new RuntimeException("无权操作该订单");

        // 检查是否已退货
        Long refundCount = orderMapper.selectCount(
                new LambdaQueryWrapper<ClothOrder>().eq(ClothOrder::getOrigOrderId, origOrderId));
        if (refundCount > 0) throw new RuntimeException("该订单已退货");

        List<ClothOrderItem> origItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<ClothOrderItem>().eq(ClothOrderItem::getOrderId, origOrderId));

        // 库存加回
        for (ClothOrderItem oi : origItems) {
            ClothSku sku = skuMapper.selectById(oi.getSkuId());
            if (sku != null) {
                sku.setStockQty(sku.getStockQty() + oi.getQty());
                skuMapper.updateById(sku);
            }
        }

        // 创建退货单
        ClothOrder refund = new ClothOrder();
        refund.setShopId(orig.getShopId());
        refund.setOrderNo(genOrderNo());
        refund.setOrderType(2);
        refund.setTotalAmount(orig.getTotalAmount());
        refund.setOrigOrderId(origOrderId);
        refund.setRemark(remark);
        refund.setOperatorId(user.getUserId());
        refund.setCreatedTime(new Date());
        orderMapper.insert(refund);

        for (ClothOrderItem oi : origItems) {
            ClothOrderItem ri = new ClothOrderItem();
            ri.setOrderId(refund.getId());
            ri.setItemId(oi.getItemId());
            ri.setSkuId(oi.getSkuId());
            ri.setItemName(oi.getItemName());
            ri.setSkuDesc(oi.getSkuDesc());
            ri.setQty(oi.getQty());
            ri.setUnitPrice(oi.getUnitPrice());
            ri.setCostPrice(oi.getCostPrice());
            orderItemMapper.insert(ri);
        }
        return refund;
    }

    /**
     * 订单列表（带明细）。
     */
    public List<ClothOrder> listOrders(String startDate, String endDate) {
        UserContext.LoginUser user = UserContext.current();
        LambdaQueryWrapper<ClothOrder> q = new LambdaQueryWrapper<ClothOrder>()
                .eq(ClothOrder::getShopId, user.getShopId())
                .orderByDesc(ClothOrder::getCreatedTime);
        if (startDate != null && !startDate.isEmpty())
            q.ge(ClothOrder::getCreatedTime, startDate);
        if (endDate != null && !endDate.isEmpty())
            q.lt(ClothOrder::getCreatedTime, endDate);

        List<ClothOrder> orders = orderMapper.selectList(q);
        if (orders.isEmpty()) return orders;

        List<Long> orderIds = orders.stream().map(ClothOrder::getId).collect(Collectors.toList());
        List<ClothOrderItem> allItems = orderItemMapper.selectByOrderIds(orderIds);
        Map<Long, List<ClothOrderItem>> itemsByOrder = allItems.stream()
                .collect(Collectors.groupingBy(ClothOrderItem::getOrderId));
        orders.forEach(o -> o.setItems(itemsByOrder.getOrDefault(o.getId(), Collections.emptyList())));
        return orders;
    }

    /**
     * 销售报表聚合（仅统计 order_type=1 销售单）。
     */
    public Map<String, Object> salesReport(String startDate, String endDate) {
        List<ClothOrder> orders = listOrders(startDate, endDate);
        List<ClothOrder> sales  = orders.stream()
                .filter(o -> o.getOrderType() == 1).collect(Collectors.toList());

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost  = BigDecimal.ZERO;
        int orderCount        = sales.size();

        Map<Long, Map<String, Object>> itemMap = new LinkedHashMap<>();
        for (ClothOrder o : sales) {
            for (ClothOrderItem li : o.getItems()) {
                BigDecimal price = li.getUnitPrice() != null ? li.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal cost  = li.getCostPrice() != null ? li.getCostPrice() : BigDecimal.ZERO;
                BigDecimal rev   = price.multiply(BigDecimal.valueOf(li.getQty()));
                BigDecimal cos   = cost .multiply(BigDecimal.valueOf(li.getQty()));
                totalSales = totalSales.add(rev);
                totalCost  = totalCost .add(cos);

                itemMap.computeIfAbsent(li.getItemId(), k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("itemId",   li.getItemId());
                    m.put("itemName", li.getItemName());
                    m.put("qty",   0);
                    m.put("sales", BigDecimal.ZERO);
                    m.put("cost",  BigDecimal.ZERO);
                    return m;
                });
                Map<String, Object> m = itemMap.get(li.getItemId());
                m.put("qty",   (int) m.get("qty") + li.getQty());
                m.put("sales", ((BigDecimal) m.get("sales")).add(rev));
                m.put("cost",  ((BigDecimal) m.get("cost")) .add(cos));
            }
        }
        List<Map<String, Object>> itemList = new ArrayList<>(itemMap.values());
        itemList.forEach(m -> m.put("profit",
                ((BigDecimal) m.get("sales")).subtract((BigDecimal) m.get("cost"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSales",  totalSales);
        summary.put("totalCost",   totalCost);
        summary.put("grossProfit", totalSales.subtract(totalCost));
        summary.put("orderCount",  orderCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items",   itemList);
        return result;
    }

    // ── 工具方法 ──────────────────────────────
    private static String buildSkuDesc(ClothSku sku) {
        List<String> parts = new ArrayList<>();
        if (sku.getColor() != null && !sku.getColor().isEmpty()) parts.add(sku.getColor());
        if (sku.getSize()  != null && !sku.getSize() .isEmpty()) parts.add(sku.getSize());
        return parts.isEmpty() ? "默认规格" : String.join("/", parts);
    }
    private static Long toLong(Object v) {
        if (v == null) throw new RuntimeException("skuId 不能为空");
        return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
    }
    private static int toInt(Object v) {
        if (v == null) return 1;
        return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
    }
    private static BigDecimal toBD(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number)     return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }
}

package com.UiUtil.inventory.service;

/**
 * 销售订单服务：创建销售/退货订单（自动扣减或回补 SKU 库存）、分页查询订单列表，以及销售报表统计。
 */
import com.UiUtil.inventory.entity.*;
import com.UiUtil.inventory.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
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
            oi.setShopId(user.getShopId());
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
            ri.setShopId(orig.getShopId());
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
     * 销售报表聚合：
     * <ul>
     *   <li>销售单（type=1）按正向计入销售额/成本；</li>
     *   <li>退货单（type=2）按反向冲减（与 listOrders 时间范围一致）；</li>
     *   <li>若销售发生在本统计期内、退货发生在期外，则本期内不再计入该笔销售，避免“已退仍算利润”。</li>
     *   <li>若销售与退货均在本期内，则销售 + 退货冲减，净额正确。</li>
     * </ul>
     */
    public Map<String, Object> salesReport(String startDate, String endDate) {
        UserContext.LoginUser user = UserContext.current();
        Long shopId = user.getShopId();

        // 全店退货单：orig_order_id -> 退货单（整单退仅一条）
        List<ClothOrder> allRefunds = orderMapper.selectList(
                new LambdaQueryWrapper<ClothOrder>()
                        .eq(ClothOrder::getShopId, shopId)
                        .eq(ClothOrder::getOrderType, 2));
        Map<Long, ClothOrder> refundByOrigSaleId = new HashMap<>();
        for (ClothOrder r : allRefunds) {
            if (r.getOrigOrderId() != null) {
                refundByOrigSaleId.put(r.getOrigOrderId(), r);
            }
        }

        List<ClothOrder> orders = listOrders(startDate, endDate);

        BigDecimal[] totals = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        int saleOrderCount    = 0;
        int refundOrderCount  = 0;

        Map<String, Map<String, Object>> itemMap = new LinkedHashMap<>();

        for (ClothOrder o : orders) {
            if (o.getOrderType() == null) continue;

            if (o.getOrderType() == 1) {
                ClothOrder ref = refundByOrigSaleId.get(o.getId());
                if (ref != null && !inReportRange(ref.getCreatedTime(), startDate, endDate)) {
                    // 已退货且退货不在本统计期：本期内不计入该销售，避免虚增
                    continue;
                }
                saleOrderCount++;
                accumulateOrderIntoReport(o, 1, totals, itemMap);
            } else if (o.getOrderType() == 2) {
                refundOrderCount++;
                accumulateOrderIntoReport(o, -1, totals, itemMap);
            }
        }

        BigDecimal totalSales = totals[0];
        BigDecimal totalCost  = totals[1];

        List<Map<String, Object>> itemList = new ArrayList<>(itemMap.values());
        itemList.removeIf(m -> {
            int q = (int) m.get("qty");
            BigDecimal s = (BigDecimal) m.get("sales");
            return q == 0 && s.compareTo(BigDecimal.ZERO) == 0;
        });
        itemList.forEach(m -> m.put("profit",
                ((BigDecimal) m.get("sales")).subtract((BigDecimal) m.get("cost"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSales",  totalSales);
        summary.put("totalCost",   totalCost);
        summary.put("grossProfit", totalSales.subtract(totalCost));
        summary.put("orderCount",  saleOrderCount);
        summary.put("saleOrderCount", saleOrderCount);
        summary.put("refundOrderCount", refundOrderCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items",   itemList);
        return result;
    }

    /** sign=1 销售累加，sign=-1 退货冲减；totals[0]=销售额 totals[1]=成本 */
    private void accumulateOrderIntoReport(ClothOrder o, int sign, BigDecimal[] totals,
                                           Map<String, Map<String, Object>> itemMap) {
        if (o.getItems() == null) return;
        for (ClothOrderItem li : o.getItems()) {
            int lineQty = li.getQty() != null ? li.getQty() : 0;
            BigDecimal price = li.getUnitPrice() != null ? li.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal cost  = li.getCostPrice() != null ? li.getCostPrice() : BigDecimal.ZERO;
            BigDecimal rev   = price.multiply(BigDecimal.valueOf(lineQty));
            BigDecimal cos   = cost.multiply(BigDecimal.valueOf(lineQty));

            if (sign >= 0) {
                totals[0] = totals[0].add(rev);
                totals[1] = totals[1].add(cos);
            } else {
                totals[0] = totals[0].subtract(rev);
                totals[1] = totals[1].subtract(cos);
            }

            String aggKey = aggregateKeyForReportLine(li);
            itemMap.computeIfAbsent(aggKey, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemId",   li.getItemId());
                m.put("skuId",    li.getSkuId());
                m.put("itemName", li.getItemName());
                m.put("skuDesc",  li.getSkuDesc());
                m.put("qty",   0);
                m.put("sales", BigDecimal.ZERO);
                m.put("cost",  BigDecimal.ZERO);
                return m;
            });
            Map<String, Object> m = itemMap.get(aggKey);
            int dq = sign * lineQty;
            m.put("qty", (int) m.get("qty") + dq);
            if (sign >= 0) {
                m.put("sales", ((BigDecimal) m.get("sales")).add(rev));
                m.put("cost",  ((BigDecimal) m.get("cost")).add(cos));
            } else {
                m.put("sales", ((BigDecimal) m.get("sales")).subtract(rev));
                m.put("cost",  ((BigDecimal) m.get("cost")).subtract(cos));
            }
        }
    }

    /**
     * 与 listOrders 一致：created_time &gt;= startDate 且 &lt; endDate（end 为排他上界，一般为次日 0 点字符串）。
     */
    private static boolean inReportRange(Date t, String startDate, String endDate) {
        if (t == null) return false;
        long tm = t.getTime();
        if (startDate != null && !startDate.isEmpty()) {
            long s = dayStartMillis(startDate.trim());
            if (tm < s) return false;
        }
        if (endDate != null && !endDate.isEmpty()) {
            long e = dayStartMillis(endDate.trim());
            if (tm >= e) return false;
        }
        return true;
    }

    private static long dayStartMillis(String ymd) {
        String[] p = ymd.split("-");
        if (p.length != 3) return 0L;
        LocalDate d = LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 报表行聚合键：
     * <ul>
     *   <li>有 skuId：按 SKU 跨订单汇总（同一 SKU 多笔销售合并数量/金额，属于正常汇总，不是覆盖）</li>
     *   <li>无 skuId 但有规格描述：按 itemId+描述汇总</li>
     *   <li>无 skuId 且规格为空：用订单明细主键，避免多条“看起来一样”的明细被误并成一行</li>
     * </ul>
     */
    private static String aggregateKeyForReportLine(ClothOrderItem li) {
        if (li.getSkuId() != null) {
            return "sku:" + li.getSkuId();
        }
        String desc = li.getSkuDesc() != null ? li.getSkuDesc().trim() : "";
        if (!desc.isEmpty()) {
            return "legacy:" + li.getItemId() + ":" + desc;
        }
        Long lineId = li.getId();
        if (lineId != null) {
            return "legacyLine:" + lineId;
        }
        return "legacyFallback:" + li.getItemId() + ":" + System.identityHashCode(li);
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

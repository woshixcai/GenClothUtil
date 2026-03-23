package com.UiUtil.inventory.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量增加库存：对每个商品的主 SKU（id 最小的一条）增加相同 delta。
 */
@Data
public class BatchAddStockRequest {
    private List<Long> itemIds;
    /** 可为负表示扣减，最终库存不小于 0 */
    private Integer delta;
}

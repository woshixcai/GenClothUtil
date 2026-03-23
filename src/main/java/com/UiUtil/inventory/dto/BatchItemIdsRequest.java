package com.UiUtil.inventory.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量操作：按商品 ID 列表（软删除、加库存等）。
 */
@Data
public class BatchItemIdsRequest {
    private List<Long> itemIds;
}

package com.o2o.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsDeductStockDto implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 商品 ID
     */
    private Long goodsId;

    /**
     * 扣减数量
     */
    private Integer quantity;
}

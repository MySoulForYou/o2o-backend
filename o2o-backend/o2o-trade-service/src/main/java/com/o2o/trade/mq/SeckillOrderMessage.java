package com.o2o.trade.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订单 ID (Snowflake)
     */
    private Long orderId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 秒杀券 ID
     */
    private Long voucherId;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 购买单价
     */
    private BigDecimal price;
}

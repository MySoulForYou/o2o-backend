package com.o2o.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_seckill_voucher")
public class SeckillVoucher {

    /**
     * 优惠券 ID，对应 tb_goods 中的 id
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 秒杀抢购价
     */
    private BigDecimal voucherPrice;

    /**
     * 秒杀物理库存
     */
    private Integer stock;

    /**
     * 秒杀活动开始时间
     */
    private LocalDateTime startTime;

    /**
     * 秒杀活动结束时间
     */
    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

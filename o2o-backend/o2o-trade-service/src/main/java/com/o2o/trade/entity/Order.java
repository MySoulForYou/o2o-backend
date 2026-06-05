package com.o2o.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_order")
public class Order {

    /**
     * 分布式主键 ID (Snowflake)
     * 注意：不能使用自增，因为秒杀订单在入库前即生成了 ID 供客户端轮询
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 订单号，唯一标识
     */
    private String orderNo;

    /**
     * 下单用户 ID
     */
    private Long userId;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 实付金额
     */
    private BigDecimal actualAmount;

    /**
     * 订单状态：0-待付款，1-已付款/待接单，2-配送中/待收货，3-已完成，4-已取消，5-已退款
     */
    private Integer status;

    /**
     * 收货人姓名
     */
    private String receiverName;

    /**
     * 收货人电话
     */
    private String receiverPhone;

    /**
     * 收货人详细地址
     */
    private String receiverAddress;

    /**
     * 订单类型：0-普通订单，1-秒杀订单
     */
    private Integer orderType;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

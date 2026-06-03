package com.o2o.cart.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CartVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id; // 购物车项主键 ID（在 MySQL 中）
    private Long goodsId;
    private Integer quantity;
    
    // 以下信息通过 Feign 远程调用 o2o-shop-service 组装
    private String goodsName;
    private BigDecimal price;
    private String image;
    private Long shopId;
    private Integer status; // 商品状态：0-下架，1-上架
    private Integer selected; // 是否选中：0-未选中，1-选中
}

package com.o2o.api;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class GoodsDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long shopId;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private String description;
    private Integer status; // 0-下架, 1-上架
}

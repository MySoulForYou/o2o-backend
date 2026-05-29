package com.o2o.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private String name;

    private BigDecimal price;

    private Integer stock;

    private String image;

    private String description;

    private Integer status; // 上架状态：0-下架，1-上架

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

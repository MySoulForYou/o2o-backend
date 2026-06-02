package com.o2o.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String logo;

    private String category;

    private String phone;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer status; // 营业状态：0-休息，1-营业中

    private Long ownerId; // 所属商家用户ID

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Double distance; // 距离 (LBS 动态计算，单位为米)
}

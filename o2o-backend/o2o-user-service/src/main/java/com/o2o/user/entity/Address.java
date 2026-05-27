package com.o2o.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String receiverName;

    private String receiverPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    private BigDecimal longitude; // 经度坐标 (LBS)

    private BigDecimal latitude;  // 纬度坐标 (LBS)

    private Integer isDefault;    // 是否默认地址：0-否，1-是

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

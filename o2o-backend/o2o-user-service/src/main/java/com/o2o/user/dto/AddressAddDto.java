package com.o2o.user.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddressAddDto {
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer isDefault;
}

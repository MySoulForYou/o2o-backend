package com.o2o.user.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AddressVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
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

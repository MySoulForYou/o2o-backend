package com.o2o.trade.vo;

import lombok.Data;

@Data
public class OrderCreateVo {
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
}

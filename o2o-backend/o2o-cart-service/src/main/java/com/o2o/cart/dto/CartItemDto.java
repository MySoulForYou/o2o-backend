package com.o2o.cart.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CartItemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long goodsId;
    private Integer quantity;
}

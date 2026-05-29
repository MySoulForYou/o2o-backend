package com.o2o.shop.vo;

import com.o2o.shop.entity.Review;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewVo extends Review {
    private String username;
}

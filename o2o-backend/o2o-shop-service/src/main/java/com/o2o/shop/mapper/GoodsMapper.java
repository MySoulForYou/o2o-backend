package com.o2o.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.o2o.shop.entity.Goods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface GoodsMapper extends BaseMapper<Goods> {

    @Update("UPDATE tb_goods SET stock = stock - #{quantity} WHERE id = #{goodsId} AND stock >= #{quantity}")
    int deductStock(@Param("goodsId") Long goodsId, @Param("quantity") Integer quantity);
}

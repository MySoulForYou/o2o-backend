package com.o2o.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.o2o.trade.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 扣减秒杀代金券物理库存（基于乐观锁防止超卖）
     *
     * @param id 券 ID
     * @param quantity 扣减数量
     * @return 成功更新的行数
     */
    @Update("UPDATE tb_seckill_voucher SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}

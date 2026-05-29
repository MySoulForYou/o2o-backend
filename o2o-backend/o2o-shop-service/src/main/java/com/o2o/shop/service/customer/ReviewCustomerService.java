package com.o2o.shop.service.customer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.shop.entity.Review;
import com.o2o.shop.vo.ReviewVo;

public interface ReviewCustomerService {
    /**
     * 消费者发表评价
     *
     * @param review 评价实体
     */
    void addReview(Review review);

    /**
     * 分页查询商品下的评价列表
     *
     * @param goodsId 商品 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 评价分页列表 (VO)
     */
    Page<ReviewVo> pageReviewsByGoodsId(Long goodsId, Integer page, Integer pageSize);
}

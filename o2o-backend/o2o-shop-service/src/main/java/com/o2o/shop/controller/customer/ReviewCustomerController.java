package com.o2o.shop.controller.customer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.Result;
import com.o2o.shop.entity.Review;
import com.o2o.shop.service.customer.ReviewCustomerService;
import com.o2o.shop.vo.ReviewVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop/customer/review")
public class ReviewCustomerController {

    @Autowired
    private ReviewCustomerService reviewCustomerService;

    /**
     * 消费者发表评价接口
     *
     * @param review 评价实体
     * @return 统一返回结果
     */
    @PostMapping
    public Result<Void> addReview(@RequestBody Review review) {
        reviewCustomerService.addReview(review);
        return Result.ok();
    }

    /**
     * 分页查询商品下的评价列表接口
     *
     * @param goodsId 商品 ID
     * @param page 页码 (默认 1)
     * @param pageSize 每页条数 (默认 10)
     * @return 统一返回分页结果
     */
    @GetMapping("/goods/{goodsId}")
    public Result<Page<ReviewVo>> pageReviews(
            @PathVariable("goodsId") Long goodsId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Page<ReviewVo> reviewVoPage = reviewCustomerService.pageReviewsByGoodsId(goodsId, page, pageSize);
        return Result.ok(reviewVoPage);
    }
}

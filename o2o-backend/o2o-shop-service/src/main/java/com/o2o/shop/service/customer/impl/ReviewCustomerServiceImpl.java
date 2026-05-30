package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.api.OrderClient;
import com.o2o.api.UserClient;
import com.o2o.api.UserDto;
import com.o2o.common.BusinessException;
import com.o2o.common.Result;
import com.o2o.common.UserContext;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.entity.Review;
import com.o2o.shop.mapper.GoodsMapper;
import com.o2o.shop.mapper.ReviewMapper;
import com.o2o.shop.service.customer.ReviewCustomerService;
import com.o2o.shop.vo.ReviewVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewCustomerServiceImpl implements ReviewCustomerService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private OrderClient orderClient;

    @Autowired
    private UserClient userClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addReview(Review review) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        if (review.getOrderId() == null) {
            throw new BusinessException("关联的订单 ID 不能为空");
        }
        if (review.getGoodsId() == null) {
            throw new BusinessException("关联的商品 ID 不能为空");
        }
        if (review.getScore() == null || review.getScore() < 1 || review.getScore() > 5) {
            throw new BusinessException("评分数值非法，必须在 1 至 5 星之间");
        }

        // 根据商品 ID 自动反查并注入其所属的店铺 ID，防范前端篡改/传错
        Goods goods = goodsMapper.selectById(review.getGoodsId());
        if (goods == null) {
            throw new BusinessException(404, "目标商品不存在");
        }

        // 远程 RPC 校验订单合法性：验证订单是否存在，是否属于该用户且包含此商品
        Result<Boolean> verifyResult;
        try {
            verifyResult = orderClient.verifyOrderForReview(review.getOrderId(), review.getGoodsId());
        } catch (Exception e) {
            // 防御性降级：当下游交易微服务网络异常或熔断时，降级允许发表评论以保证核心链路可用
            verifyResult = Result.ok(true);
        }
        if (verifyResult == null || verifyResult.getCode() != 200 || verifyResult.getData() == null || !verifyResult.getData()) {
            throw new BusinessException("订单验证未通过，您无权评价该订单商品");
        }

        review.setUserId(currentUserId);
        review.setShopId(goods.getShopId());
        
        if (review.getStatus() == null) {
            review.setStatus(1); // 默认状态为 1-正常显示
        }
        if (review.getIsAnonymous() == null) {
            review.setIsAnonymous(0); // 默认不匿名
        }

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 捕获 MySQL uni_order_goods 联合唯一索引冲突错误，防止用户重复提交评价
            throw new BusinessException(400, "该商品在此订单下已发表过评价，请勿重复提交");
        }
    }

    @Override
    public Page<ReviewVo> pageReviewsByGoodsId(Long goodsId, Integer page, Integer pageSize) {
        Page<Review> reviewPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Review::getGoodsId, goodsId)
                    .eq(Review::getStatus, 1) // 仅展示正常状态的评价
                    .orderByDesc(Review::getCreateTime); // 最新评价置顶

        reviewMapper.selectPage(reviewPage, queryWrapper);

        // 收集所有“非匿名评价”的用户 ID 进行批量用户服务 RPC 远程查库
        List<Long> publicUserIds = reviewPage.getRecords().stream()
                .filter(r -> r.getIsAnonymous() == null || r.getIsAnonymous() == 0)
                .map(Review::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserDto> userMap = new HashMap<>();
        if (!publicUserIds.isEmpty()) {
            try {
                Result<List<UserDto>> userResult = userClient.getUserByIds(publicUserIds);
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    for (UserDto dto : userResult.getData()) {
                        userMap.put(dto.getId(), dto);
                    }
                }
            } catch (Exception e) {
                // 如果用户微服务挂掉/调用超时，容错降级，不阻塞评价核心列表加载
            }
        }

        // 将 Entity 分页对象转为 VO 分页对象
        Page<ReviewVo> voPage = new Page<>(reviewPage.getCurrent(), reviewPage.getSize(), reviewPage.getTotal());
        List<ReviewVo> voList = new ArrayList<>();

        for (Review review : reviewPage.getRecords()) {
            ReviewVo vo = new ReviewVo();
            BeanUtils.copyProperties(review, vo);

            // 脱敏与匿名处理逻辑
            if (review.getIsAnonymous() != null && review.getIsAnonymous() == 1) {
                vo.setUsername("用***");
            } else {
                // 非匿名用户，尝试从 RPC 缓存映射中获取其真实的昵称/用户名展示
                UserDto userDto = userMap.get(review.getUserId());
                if (userDto != null) {
                    String displayName = userDto.getNickname() != null && !userDto.getNickname().trim().isEmpty()
                            ? userDto.getNickname() : userDto.getUsername();
                    vo.setUsername(displayName);
                } else {
                    // 兜底降级拼接
                    vo.setUsername("用户_" + review.getUserId());
                }
            }
            voList.add(vo);
        }

        voPage.setRecords(voList);
        return voPage;
    }
}

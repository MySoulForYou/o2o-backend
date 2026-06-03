package com.o2o.cart.service;

import com.o2o.api.CartVo;
import java.util.List;

public interface CartService {
    /**
     * 添加商品到购物车（MySQL + Redis 同步双写）
     *
     * @param goodsId  商品 ID
     * @param quantity 加购数量
     */
    void addCartItem(Long goodsId, Integer quantity);

    /**
     * 更新购物车商品数量
     *
     * @param goodsId  商品 ID
     * @param quantity 商品数量
     */
    void updateCartItem(Long goodsId, Integer quantity);

    /**
     * 删除购物车中的商品
     *
     * @param goodsId 商品 ID
     */
    void deleteCartItem(Long goodsId);

    /**
     * 清空购物车
     */
    void clearCart();

    /**
     * 获取购物车详情列表
     *
     * @return 购物车详情列表
     */
    List<CartVo> getCartList();

    /**
     * 更新购物车商品的勾选状态
     *
     * @param goodsId  商品 ID (如果为 null 则代表更新全部商品)
     * @param selected 选中状态：0-未选中，1-选中
     */
    void updateSelectStatus(Long goodsId, Integer selected);

    /**
     * 获取当前用户已勾选的购物车商品详情列表
     *
     * @return 选中商品详情列表
     */
    List<CartVo> getSelectedCartList();

    /**
     * 批量删除购物车中的商品项（MySQL + Redis 同步清理）
     *
     * @param goodsIds 商品 ID 列表
     */
    void deleteCartItems(List<Long> goodsIds);

    /**
     * 一键清空购物车内所有失效/下架商品
     */
    void clearExpiredItems();
}



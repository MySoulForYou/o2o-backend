package com.o2o.cart.controller;

import com.o2o.cart.dto.CartItemDto;
import com.o2o.cart.service.CartService;
import com.o2o.api.CartVo;
import com.o2o.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 添加商品到购物车
     *
     * @param dto 商品ID与加购数量
     * @return 统一返回结果
     */
    @PostMapping("/add")
    public Result<?> addCartItem(@RequestBody CartItemDto dto) {
        cartService.addCartItem(dto.getGoodsId(), dto.getQuantity());
        return Result.ok();
    }

    /**
     * 更新购物车中商品数量
     *
     * @param dto 商品ID与更新后的数量
     * @return 统一返回结果
     */
    @PutMapping("/update")
    public Result<?> updateCartItem(@RequestBody CartItemDto dto) {
        cartService.updateCartItem(dto.getGoodsId(), dto.getQuantity());
        return Result.ok();
    }

    /**
     * 删除购物车中的商品
     *
     * @param goodsId 商品ID
     * @return 统一返回结果
     */
    @DeleteMapping("/delete/{goodsId}")
    public Result<?> deleteCartItem(@PathVariable("goodsId") Long goodsId) {
        cartService.deleteCartItem(goodsId);
        return Result.ok();
    }

    /**
     * 清空当前用户的购物车
     *
     * @return 统一返回结果
     */
    @DeleteMapping("/clear")
    public Result<?> clearCart() {
        cartService.clearCart();
        return Result.ok();
    }

    /**
     * 获取当前用户的购物车列表
     *
     * @return 购物车商品详情列表
     */
    @GetMapping("/list")
    public Result<List<CartVo>> getCartList() {
        List<CartVo> list = cartService.getCartList();
        return Result.ok(list);
    }

    /**
     * 更新单品或全部商品的勾选状态
     *
     * @param goodsId  商品 ID（可选，传 null 则代表全选或全不选）
     * @param selected 是否选中：0-否，1-是
     * @return 统一返回结果
     */
    @PutMapping("/select")
    public Result<?> updateSelectStatus(
            @RequestParam(value = "goodsId", required = false) Long goodsId,
            @RequestParam("selected") Integer selected) {
        cartService.updateSelectStatus(goodsId, selected);
        return Result.ok();
    }

    /**
     * 获取当前用户已勾选的购物车商品列表
     *
     * @return 选中商品详情列表
     */
    @GetMapping("/list/selected")
    public Result<List<CartVo>> getSelectedCartItems() {
        List<CartVo> list = cartService.getSelectedCartList();
        return Result.ok(list);
    }

    /**
     * 批量删除购物车中的商品项
     *
     * @param goodsIds 商品 ID 列表
     * @return 统一返回结果
     */
    @DeleteMapping("/delete/batch")
    public Result<?> deleteCartItems(@RequestParam("goodsIds") List<Long> goodsIds) {
        cartService.deleteCartItems(goodsIds);
        return Result.ok();
    }

    /**
     * 一键清空当前用户购物车内的所有失效/下架商品
     *
     * @return 统一返回结果
     */
    @DeleteMapping("/clear/expired")
    public Result<?> clearExpiredItems() {
        cartService.clearExpiredItems();
        return Result.ok();
    }
}

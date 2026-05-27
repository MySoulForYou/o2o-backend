package com.o2o.user.controller;

import com.o2o.common.Result;
import com.o2o.common.UserContext;
import com.o2o.user.dto.AddressAddDto;
import com.o2o.user.service.AddressService;
import com.o2o.user.vo.AddressVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/address")
public class AddressController {

    @Autowired
    private AddressService addressService;

    /**
     * 获取用户所有收货地址列表 (需登录)
     *
     * @return 统一返回结果 Result，包含地址 VO 列表
     */
    @GetMapping("/list")
    public Result<List<AddressVo>> listAddress() {
        Long userId = UserContext.getUserId();
        List<AddressVo> list = addressService.listAddress(userId);
        return Result.ok(list);
    }

    /**
     * 新增收货地址 (需登录)
     *
     * @param dto    地址参数 DTO
     * @return 统一返回结果 Result
     */
    @PostMapping("/add")
    public Result<?> addAddress(@RequestBody AddressAddDto dto) {
        Long userId = UserContext.getUserId();
        addressService.addAddress(userId, dto);
        return Result.ok();
    }

    /**
     * 删除收货地址 (需登录)
     *
     * @param addressId 地址 ID (路径参数)
     * @return 统一返回结果 Result
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteAddress(@PathVariable("id") Long addressId) {
        Long userId = UserContext.getUserId();
        addressService.deleteAddress(userId, addressId);
        return Result.ok();
    }

    /**
     * 设为默认收货地址 (需登录)
     *
     * @param addressId 地址 ID (路径参数)
     * @return 统一返回结果 Result
     */
    @PutMapping("/default/{id}")
    public Result<?> setDefault(@PathVariable("id") Long addressId) {
        Long userId = UserContext.getUserId();
        addressService.setDefault(userId, addressId);
        return Result.ok();
    }
}

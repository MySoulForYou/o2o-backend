package com.o2o.user.service;

import com.o2o.user.dto.AddressAddDto;
import com.o2o.user.vo.AddressVo;

import java.util.List;

public interface AddressService {

    /**
     * 查询用户所有收货地址
     *
     * @param userId 用户ID
     * @return 地址 VO 列表
     */
    List<AddressVo> listAddress(Long userId);

    /**
     * 新增收货地址
     *
     * @param userId 用户ID
     * @param dto    地址参数 DTO
     */
    void addAddress(Long userId, AddressAddDto dto);

    /**
     * 删除收货地址 (防越权)
     *
     * @param userId    用户ID
     * @param addressId 地址ID
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * 设置默认收货地址 (防越权)
     *
     * @param userId    用户ID
     * @param addressId 地址ID
     */
    void setDefault(Long userId, Long addressId);
}

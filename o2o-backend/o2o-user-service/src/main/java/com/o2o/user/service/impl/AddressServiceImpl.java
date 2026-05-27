package com.o2o.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.o2o.common.BusinessException;
import com.o2o.user.dto.AddressAddDto;
import com.o2o.user.entity.Address;
import com.o2o.user.mapper.AddressMapper;
import com.o2o.user.service.AddressService;
import com.o2o.user.vo.AddressVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    @Override
    public List<AddressVo> listAddress(Long userId) {
        // 根据用户ID查询地址列表，默认地址排在最前面，然后按创建时间降序
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreateTime);
        
        List<Address> addresses = addressMapper.selectList(queryWrapper);
        
        // 转换为 VO 列表
        return addresses.stream().map(address -> {
            AddressVo vo = new AddressVo();
            BeanUtils.copyProperties(address, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    // 最大收货地址数量上限限制
    private static final int MAX_ADDRESS_LIMIT = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAddress(Long userId, AddressAddDto dto) {
        // 1. 校验收货地址数量是否已达上限
        Long currentCount = addressMapper.selectCount(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId)
        );
        if (currentCount >= MAX_ADDRESS_LIMIT) {
            log.warn("新增地址失败，已达数量上限: {}, userId: {}", currentCount, userId);
            throw new BusinessException("您的收货地址已达上限，最多添加" + MAX_ADDRESS_LIMIT + "个地址");
        }

        // 2. 如果设置为默认地址，先清空该用户已有的默认地址
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        // 3. 保存新地址
        Address address = new Address();
        BeanUtils.copyProperties(dto, address);
        address.setUserId(userId);
        
        // 第一次添加，若未指定是否默认，默认设为非默认
        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }

        addressMapper.insert(address);
        log.info("用户新增地址成功, userId: {}, addressId: {}", userId, address.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long addressId) {
        // 1. 校验地址所有权，防止越权删除
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            log.warn("非法删除地址请求! 操作用户: {}, 目标地址ID: {}", userId, addressId);
            throw new BusinessException("地址不存在或无权操作");
        }

        // 2. 执行物理删除
        addressMapper.deleteById(addressId);
        log.info("用户删除地址成功, userId: {}, addressId: {}", userId, addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        // 1. 校验地址所有权
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            log.warn("非法设置默认地址请求! 操作用户: {}, 目标地址ID: {}", userId, addressId);
            throw new BusinessException("地址不存在或无权操作");
        }

        // 2. 清空该用户所有旧的默认地址
        clearDefaultAddress(userId);

        // 3. 将当前地址设为默认
        address.setIsDefault(1);
        addressMapper.updateById(address);
        log.info("设置默认地址成功, userId: {}, addressId: {}", userId, addressId);
    }

    /**
     * 将用户的所有地址均设为非默认 (is_default = 0)
     */
    private void clearDefaultAddress(Long userId) {
        LambdaUpdateWrapper<Address> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Address::getUserId, userId)
                .set(Address::getIsDefault, 0);
        addressMapper.update(null, updateWrapper);
    }
}

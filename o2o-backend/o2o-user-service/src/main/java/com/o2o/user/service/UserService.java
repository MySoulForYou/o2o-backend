package com.o2o.user.service;

import com.o2o.user.dto.UserUpdateDto;
import com.o2o.user.vo.UserVo;

import java.util.Map;

public interface UserService {
    
    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 明文密码
     * @param phone    手机号
     */
    void register(String username, String password, String phone);

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录成功后的 Token 及用户信息 Map
     */
    Map<String, Object> login(String username, String password);

    /**
     * 获取当前登录用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息展示 VO
     */
    UserVo getUserInfo(Long userId);

    /**
     * 修改当前登录用户信息
     *
     * @param userId 用户 ID
     * @param dto    修改参数
     */
    void updateUserInfo(Long userId, UserUpdateDto dto);

    /**
     * 批量根据用户 ID 列表获取用户信息
     *
     * @param ids 用户 ID 列表
     * @return 用户 DTO 列表
     */
    java.util.List<com.o2o.api.UserDto> getUserByIds(java.util.List<Long> ids);
}

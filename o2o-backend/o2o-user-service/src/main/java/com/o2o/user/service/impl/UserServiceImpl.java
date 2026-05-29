package com.o2o.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.common.BusinessException;
import com.o2o.common.JwtUtils;
import com.o2o.user.dto.UserUpdateDto;
import com.o2o.user.entity.User;
import com.o2o.user.mapper.UserMapper;
import com.o2o.user.service.UserService;
import com.o2o.user.utils.PasswordUtils;
import com.o2o.user.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    // Token 有效期设为 2 小时 (2 * 60 * 60 * 1000 毫秒)
    private static final long TOKEN_EXPIRE_TIME = 7200000L;

    @Override
    public void register(String username, String password, String phone) {
        // 1. 格式校验（防脏数据）
        if (username == null || !username.matches("^[a-zA-Z0-9_]{4,20}$")) {
            log.warn("注册失败，用户名格式非法: {}", username);
            throw new BusinessException("用户名格式不正确，需为4-20位字母、数字或下划线");
        }
        if (password == null || password.length() < 6 || password.length() > 32) {
            log.warn("注册失败，密码长度非法");
            throw new BusinessException("密码格式不正确，长度需在6-32位之间");
        }
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            log.warn("注册失败，手机号格式非法: {}", phone);
            throw new BusinessException("手机号格式不正确");
        }

        // 2. 校验用户名是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User existUser = userMapper.selectOne(queryWrapper);

        if (existUser != null) {
            log.warn("注册失败，用户名已存在: {}", username);
            throw new BusinessException("用户名已存在");
        }

        // 3. 校验手机号是否已存在
        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(User::getPhone, phone);
        User existPhone = userMapper.selectOne(phoneWrapper);

        if (existPhone != null) {
            log.warn("注册失败，手机号已被注册: {}", phone);
            throw new BusinessException("该手机号已被注册");
        }

        // 4. 使用 BCrypt 算法对密码进行加密
        String encryptedPassword = PasswordUtils.encrypt(password);

        // 5. 构建用户实体并保存到数据库
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptedPassword);
        user.setPhone(phone);
        user.setNickname("用户_" + username); // 设置默认昵称
        user.setStatus(1); // 默认启用状态

        userMapper.insert(user);
        log.info("用户注册成功: {}", username);
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        // 1. 根据用户名查询用户信息
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User dbUser = userMapper.selectOne(queryWrapper);

        if (dbUser == null) {
            log.warn("登录失败，用户不存在: {}", username);
            throw new BusinessException("用户名或密码错误");
        }

        // 2. 校验账号状态是否被禁用
        if (dbUser.getStatus() == 0) {
            log.warn("登录失败，账号已被禁用: {}", username);
            throw new BusinessException("该账号已被禁用，请联系管理员");
        }

        // 3. 使用 BCrypt 进行密码比对
        boolean isPasswordMatch = PasswordUtils.verify(password, dbUser.getPassword());
        if (!isPasswordMatch) {
            log.warn("登录失败，密码不匹配: {}", username);
            throw new BusinessException("用户名或密码错误");
        }

        // 4. 比对成功，利用 JwtUtils 生成 Token
        String token = JwtUtils.createToken(dbUser.getId(), dbUser.getUsername(), TOKEN_EXPIRE_TIME);

        // 5. 组装返回数据 (Token + 用户基本信息)
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", Map.of(
                "id", dbUser.getId(),
                "username", dbUser.getUsername(),
                "nickname", dbUser.getNickname(),
                "avatar", dbUser.getAvatar() != null ? dbUser.getAvatar() : ""
        ));

        log.info("用户登录成功: {}", username);
        return result;
    }

    @Override
    public UserVo getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("查询用户信息失败，用户ID不存在: {}", userId);
            throw new BusinessException("用户不存在");
        }
        
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        return userVo;
    }

    @Override
    public void updateUserInfo(Long userId, UserUpdateDto dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("修改用户信息失败，用户ID不存在: {}", userId);
            throw new BusinessException("用户不存在");
        }

        // 仅在传参不为空时更新字段
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }

        userMapper.updateById(user);
        log.info("用户信息更新成功，userId: {}", userId);
    }

    @Override
    public java.util.List<com.o2o.api.UserDto> getUserByIds(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        java.util.List<User> users = userMapper.selectBatchIds(ids);
        java.util.List<com.o2o.api.UserDto> dtoList = new java.util.ArrayList<>();

        for (User user : users) {
            com.o2o.api.UserDto dto = new com.o2o.api.UserDto();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname());
            dto.setPhone(user.getPhone());
            dto.setAvatar(user.getAvatar());
            dtoList.add(dto);
        }

        return dtoList;
    }
}

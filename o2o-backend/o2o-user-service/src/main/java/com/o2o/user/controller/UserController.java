package com.o2o.user.controller;

import com.o2o.common.Result;
import com.o2o.common.UserContext;
import com.o2o.user.dto.UserLoginDto;
import com.o2o.user.dto.UserRegisterDto;
import com.o2o.user.dto.UserUpdateDto;
import com.o2o.user.service.UserService;
import com.o2o.user.vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     *
     * @param dto 注册入参 DTO
     * @return 统一返回结果 Result
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody UserRegisterDto dto) {
        userService.register(dto.getUsername(), dto.getPassword(), dto.getPhone());
        return Result.ok();
    }

    /**
     * 用户登录接口
     *
     * @param dto 登录入参 DTO
     * @return 统一返回结果 Result，包含 Token 及用户信息
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody UserLoginDto dto) {
        Map<String, Object> loginData = userService.login(dto.getUsername(), dto.getPassword());
        return Result.ok(loginData);
    }

    /**
     * 获取当前登录用户信息接口 (受网关保护，需登录)
     *
     * @return 统一返回结果 Result，包含用户数据 VO
     */
    @GetMapping("/info")
    public Result<UserVo> getUserInfo() {
        Long userId = UserContext.getUserId();
        UserVo userVo = userService.getUserInfo(userId);
        return Result.ok(userVo);
    }

    /**
     * 修改个人资料接口 (受网关保护，需登录)
     *
     * @param dto    修改的参数体 DTO
     * @return 统一返回结果 Result
     */
    @PutMapping("/update")
    public Result<?> updateUserInfo(@RequestBody UserUpdateDto dto) {
        Long userId = UserContext.getUserId();
        userService.updateUserInfo(userId, dto);
        return Result.ok();
    }

    /**
     * 根据多个用户 ID 批量获取用户信息接口 (提供给其他微服务 RPC 调用，受网关内部透传保护)
     *
     * @param ids 用户 ID 列表
     * @return 统一返回结果 Result，包含用户 DTO 列表
     */
    @GetMapping("/list/batch")
    public Result<java.util.List<com.o2o.api.UserDto>> getUserByIds(@RequestParam("ids") java.util.List<Long> ids) {
        java.util.List<com.o2o.api.UserDto> userDtoList = userService.getUserByIds(ids);
        return Result.ok(userDtoList);
    }
}

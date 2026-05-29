package com.o2o.api;

import com.o2o.api.fallback.UserClientFallback;
import com.o2o.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "o2o-user-service", contextId = "userClient", fallback = UserClientFallback.class)
public interface UserClient {
    /**
     * 根据多个用户 ID 批量获取用户信息 (用于评价列表渲染非匿名昵称)
     *
     * @param ids 用户 ID 列表
     * @return 用户 DTO 列表
     */
    @GetMapping("/user/list/batch")
    Result<List<UserDto>> getUserByIds(@RequestParam("ids") List<Long> ids);
}

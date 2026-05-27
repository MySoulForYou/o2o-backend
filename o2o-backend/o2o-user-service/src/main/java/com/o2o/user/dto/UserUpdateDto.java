package com.o2o.user.dto;

import lombok.Data;

@Data
public class UserUpdateDto {
    private String nickname;
    private String avatar;
    private String phone;
}

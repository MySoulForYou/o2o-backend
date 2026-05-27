package com.o2o.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user")
public class User {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    
    private String password;
    
    private String phone;
    
    private String nickname;
    
    private String avatar;
    
    private Integer status; // 0-禁用，1-正常
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

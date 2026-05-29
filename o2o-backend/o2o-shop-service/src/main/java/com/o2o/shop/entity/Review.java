package com.o2o.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long goodsId;

    private Long shopId;

    private Long userId;

    private Integer score; // 评分：1至5星

    private String content;

    private String images; // 晒图链接列表 (JSON 字符串，如 ["url1", "url2"])

    private Integer status; // 审核状态：0-隐藏/违规，1-正常显示，2-待审核

    private Integer isAnonymous; // 是否匿名：0-公开，1-匿名

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

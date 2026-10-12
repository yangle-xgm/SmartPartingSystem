package com.smartparking.notify.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业主反馈/投诉
 */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userName;

    /** 类型：1投诉 2建议 3咨询 4紧急 */
    private Integer type;

    private String title;

    private String content;

    /** 0待处理 1处理中 2已回复 3已关闭 */
    private Integer status;

    private String reply;

    private LocalDateTime createTime;

    private LocalDateTime replyTime;
}

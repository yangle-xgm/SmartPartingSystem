package com.smartparking.parking.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车位预约记录
 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private Long userId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0已预约 1已入场 2已过期释放 3已取消 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

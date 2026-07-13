package com.zxsh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预约到店记录（智能客服 Function Calling 使用）
 */
@Data
@TableName("tb_reservation")
public class Reservation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 店铺 ID */
    private Long shopId;

    /** 客户姓名 */
    private String userName;

    /** 联系电话 */
    private String phone;

    /** 预约到店时间 */
    private LocalDateTime arriveTime;

    /** 状态：0 已预约, 1 已到店, 2 已取消 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

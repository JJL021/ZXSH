package com.zxsh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxsh.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约到店记录 Mapper
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
}

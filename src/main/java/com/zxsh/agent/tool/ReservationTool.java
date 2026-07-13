package com.zxsh.agent.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zxsh.dto.UserDTO;
import com.zxsh.entity.Reservation;
import com.zxsh.entity.Shop;
import com.zxsh.mapper.ReservationMapper;
import com.zxsh.service.IShopService;
import com.zxsh.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * 预约到店工具 —— 供 LLM Function Calling 调用。
 * <p>
 * LLM 从用户输入中提取姓名、电话、时间、商家名称，调用本工具创建预约记录。
 */
@Slf4j
@Component
public class ReservationTool {

    @Resource
    private IShopService shopService;

    @Resource
    private ReservationMapper reservationMapper;

    /**
     * 创建预约到店记录
     *
     * @param userName        客户姓名
     * @param phone           联系电话
     * @param reservationTime 预约到店时间（字符串，LLM 传入格式如 "2026-07-09 18:00"）
     * @param shopName        商家名称关键字
     * @return 预约确认信息的 JSON
     */
    @Tool("预约到店，需提供姓名、电话、预约时间和商家名称。返回预约编号和确认信息")
    public String makeReservation(
            @P("客户姓名") String userName,
            @P("联系电话") String phone,
            @P("预约到店时间，如 2026-07-09 18:00") String reservationTime,
            @P("商家名称关键字") String shopName) {

        // 1. 解析时间
        LocalDateTime arriveTime;
        try {
            arriveTime = LocalDateTime.parse(reservationTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException e) {
            return "预约时间格式不正确，请使用 yyyy-MM-dd HH:mm 格式，例如 2026-07-09 18:00";
        }

        // 2. 模糊查询店铺
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Shop::getName, shopName).last("LIMIT 1");
        Shop shop = shopService.getOne(wrapper);
        if (shop == null) {
            return "未找到名称为「" + shopName + "」的商家，请确认名称后重试。";
        }

        // 3. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = (user != null) ? user.getId() : null;

        // 4. 创建预约记录
        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setShopId(shop.getId());
        reservation.setUserName(userName);
        reservation.setPhone(phone);
        reservation.setArriveTime(arriveTime);
        reservation.setStatus(0); // 已预约

        reservationMapper.insert(reservation);
        log.info("ReservationTool.makeReservation: 预约成功 id={}, shopName={}, arriveTime={}",
                reservation.getId(), shop.getName(), arriveTime);

        // 5. 返回确认信息
        Map<String, Object> result = new HashMap<>();
        result.put("reservationId", reservation.getId());
        result.put("shopName", shop.getName());
        result.put("shopAddress", shop.getAddress());
        result.put("userName", userName);
        result.put("phone", phone);
        result.put("arriveTime", arriveTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return JSONUtil.toJsonStr(result);
    }
}

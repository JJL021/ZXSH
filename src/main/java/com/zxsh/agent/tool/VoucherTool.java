package com.zxsh.agent.tool;

import cn.hutool.json.JSONUtil;
import com.zxsh.dto.UserDTO;
import com.zxsh.entity.Voucher;
import com.zxsh.entity.VoucherOrder;
import com.zxsh.service.IVoucherOrderService;
import com.zxsh.service.IVoucherService;
import com.zxsh.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券查询工具 —— 供 LLM Function Calling 调用。
 * <p>
 * 获取用户身份通过 {@link UserHolder#getUser()}（ThreadLocal），
 * 依赖 RefreshTokenInterceptor 预先从请求头 token 中解析用户信息。
 */
@Slf4j
@Component
public class VoucherTool {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IVoucherService voucherService;

    /**
     * 查询当前登录用户拥有的所有优惠券（已领取/已使用的券记录）
     */
    @Tool("查询当前用户拥有的所有优惠券，返回券标题、金额、状态等")
    public String queryMyVouchers() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return "您尚未登录，无法查询优惠券。请先登录。";
        }

        // 查用户的券订单（关联 tb_voucher 获取券详情）
        List<VoucherOrder> orders = voucherOrderService.query()
                .eq("user_id", user.getId())
                .list();

        if (orders.isEmpty()) {
            return "您当前没有任何优惠券。";
        }

        log.info("VoucherTool.queryMyVouchers: userId={}, 命中 {} 条", user.getId(), orders.size());

        // 组装返回：券标题 + 金额 + 状态
        List<Map<String, Object>> result = orders.stream().map(order -> {
            Voucher voucher = voucherService.getById(order.getVoucherId());
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", order.getId());
            map.put("title", voucher != null ? voucher.getTitle() : "未知");
            map.put("actualValue", voucher != null ? voucher.getActualValue() : 0);
            map.put("status", translateStatus(order.getStatus()));
            return map;
        }).toList();

        return JSONUtil.toJsonStr(result);
    }

    /** 券状态：1 未支付, 2 已支付, 3 已核销, 4 已取消, 5 退款中, 6 已退款 */
    private String translateStatus(Integer status) {
        return switch (status) {
            case 1 -> "未支付";
            case 2 -> "已支付";
            case 3 -> "已核销";
            case 4 -> "已取消";
            case 5 -> "退款中";
            case 6 -> "已退款";
            default -> "未知";
        };
    }
}

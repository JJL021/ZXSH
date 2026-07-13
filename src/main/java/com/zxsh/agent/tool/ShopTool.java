package com.zxsh.agent.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zxsh.entity.Shop;
import com.zxsh.entity.Voucher;
import com.zxsh.service.IShopService;
import com.zxsh.service.IVoucherService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺信息查询工具 —— 供 LLM Function Calling 调用。
 * <p>
 * 方法签名设计原则：参数全部用基础类型（String/Long），
 * 让 LLM 能从用户自然语言中直接提取值填入。
 */
@Slf4j
@Component
public class ShopTool {

    @Resource
    private IShopService shopService;

    @Resource
    private IVoucherService voucherService;

    /**
     * 按名称关键字 + 可选区域搜索店铺
     *
     * @param shopName 店铺名称关键字（如 "鲈鱼"、"开乐迪KTV"）
     * @param area     区域名称（如 "拱墅万达"、"运河上街"），可选
     * @return JSON 格式的店铺列表，包含 id、名称、地址、均价、评分、营业时间
     */
    @Tool("按名称关键字搜索店铺，可附带区域名缩小范围。返回店铺详情（地址、均价、评分、营业时间等）")
    public String searchShop(
            @P("店铺名称关键字") String shopName,
            @P("区域名称（可选，如'拱墅万达'）") String area) {

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Shop::getName, shopName);
        if (area != null && !area.isEmpty()) {
            wrapper.like(Shop::getArea, area);
        }
        wrapper.last("LIMIT 5"); // 最多返回 5 条，避免 token 溢出

        List<Shop> shops = shopService.list(wrapper);
        log.info("ShopTool.searchShop: shopName={}, area={}, 命中 {} 条", shopName, area, shops.size());

        // 精简返回字段，只保留 LLM 需要的信息
        List<Map<String, Object>> result = shops.stream().map(shop -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", shop.getId());
            map.put("name", shop.getName());
            map.put("address", shop.getAddress());
            map.put("area", shop.getArea());
            map.put("avgPrice", shop.getAvgPrice());
            map.put("score", shop.getScore() != null ? shop.getScore() / 10.0 : null);
            map.put("openHours", shop.getOpenHours());
            return map;
        }).toList();

        return JSONUtil.toJsonStr(result);
    }

    /**
     * 查询指定店铺的可用优惠券
     *
     * @param shopId 店铺 ID（由 searchShop 返回的 id 提供）
     * @return JSON 格式的优惠券列表，包含券标题、实际价值、使用规则
     */
    @Tool("查询指定店铺当前可用的优惠券列表")
    public String queryShopVouchers(
            @P("店铺ID") Long shopId) {

        List<Voucher> vouchers = voucherService.query()
                .eq("shop_id", shopId)
                .eq("status", 1) // 仅查询生效中的券
                .list();

        log.info("ShopTool.queryShopVouchers: shopId={}, 命中 {} 张券", shopId, vouchers.size());

        List<Map<String, Object>> result = vouchers.stream().map(v -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", v.getId());
            map.put("title", v.getTitle());
            map.put("actualValue", v.getActualValue()); // 实际优惠金额
            map.put("rules", v.getRules());
            return map;
        }).toList();

        return JSONUtil.toJsonStr(result);
    }
}

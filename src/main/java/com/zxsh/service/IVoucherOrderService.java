package com.zxsh.service;

import com.zxsh.dto.Result;
import com.zxsh.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucher(Long voucherId) throws InterruptedException;

    Result createVoucherOrder(Long voucherId);

    void createVoucherOrderSimple(VoucherOrder voucherOrder);
}

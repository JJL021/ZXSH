package com.zxsh.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zxsh.dto.Result;
import com.zxsh.entity.ShopType;
import com.zxsh.mapper.ShopTypeMapper;
import com.zxsh.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.List;

import static com.zxsh.utils.RedisConstants.CACHE_SHOPLIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //1.查找redis
        String key = CACHE_SHOPLIST_KEY;
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);
        //2.有，直接返回
        if(StrUtil.isNotBlank(shopTypeListJson)){
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //3.无，查找数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //4.数据库中无，返回404
        if(CollUtil.isEmpty(typeList)){
            return Result.fail("店铺类型列表信息不存在");
        }
        //5.数据库中有，写入redis
        String jsonStr = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
        //6.返回
        return Result.ok(typeList);

    }
}

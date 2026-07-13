package com.zxsh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zxsh.dto.Result;
import com.zxsh.dto.UserDTO;
import com.zxsh.entity.Follow;
import com.zxsh.mapper.FollowMapper;
import com.zxsh.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zxsh.service.IUserService;
import com.zxsh.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1. 获取用户id
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return Result.fail("请先登录！");
        }
        Long userId = user.getId();
        String key = "follows:"+userId;
        //2.判断是关注还是取消关注
        if(isFollow){
            //3.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //把关注的用户的id放入redis的set集合中 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //4.取消关注，删除数据 delete from tb_follow where user_id = ? AND follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            //从redis集合中删除
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    //查询是否关注了该用户
    @Override
    public Result isFollow(Long followUserId) {
        //1. 获取用户id
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return Result.fail("请先登录！");
        }
        Long userId = user.getId();
        //查询数据 select count(*) from tb_follow where user_id = ? AND follow_user_id = ?
        Long count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();

        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        UserDTO cur_user = UserHolder.getUser();
        if(cur_user == null){
            return Result.ok(Collections.emptyList());
        }
        Long userId = cur_user.getId();
        String key1 =  "follows:"+userId;
        String key2 =  "follows:"+id;
        //2.求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}

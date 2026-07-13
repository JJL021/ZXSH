package com.zxsh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zxsh.dto.LoginFormDTO;
import com.zxsh.dto.Result;
import com.zxsh.dto.UserDTO;
import com.zxsh.entity.User;
import com.zxsh.mapper.UserMapper;
import com.zxsh.service.IUserService;
import com.zxsh.utils.RegexUtils;
import com.zxsh.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zxsh.utils.RedisConstants.*;
import static com.zxsh.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //2. 不符合返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3. 符合生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis  set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码(需要调用第三方短信平台如阿里云)
        log.debug("发送短信验证码成功，验证码：{}",code);
        //验证码保存起来将来用户登录的时候返回给前端
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            //2. 不符合返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3. 获取redis中的验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            //3.不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if(user == null){
            //6.查不到，创建新用户，保存到数据库
            user = createUserWithPhone(phone);
        }
        //7.保存用户信息到redis
        //7.1 随机生成key，作为作为用户信息的key
        String token = UUID.randomUUID().toString(true);
        //7.2将user对象转换为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                        (fieldName,fieldValue) -> fieldValue.toString()
                ));
        //7.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //7.4设置tokenKey有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.SECONDS);
        //8. 返回这个key（事实上就是一个token）
        return Result.ok(token);
    }
    //用户签到
    @Override
    public Result sign() {
        //1.获取当前用户
        Long id = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY+id+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.获取当前用户
        Long id = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY+id+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止到今天的所有签到记录，的是十进制数
        //BITFIELD sign:5:202203 GET u14 0
        //下面api结果是集合是因为该命令可以同时做get，set，因此可能有多种结果
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)

        );
        if(result == null || result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        //我们只做了get操作，因此0索引就是该命令返回的十进制数
        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }
        //6. 循环遍历
        int count = 0;
        while (true){
            //6.1 让该数字与1做与运算，的到最后一个bit
            if((num & 1) == 0){
                //如果是0，未签到，结束
                break;
            }else{
                //如果是1，已签到，计数器+1
                count++;
            }
            //把数字右移一位更新最低位
            // >>>= 无符号右移并赋值
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //存入数据库
        save(user);
        return user;
    }
}

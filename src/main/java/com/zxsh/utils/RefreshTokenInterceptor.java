package com.zxsh.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zxsh.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zxsh.utils.RedisConstants.LOGIN_USER_KEY;
import static com.zxsh.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 这个类的对象是我们手动创建的，new的，不是@component等注解出来的，即不是spring创建的，
 * 因此spring不能帮我们实现依赖注入，因此不能在里面使用@Autowired注解 ，@Resourse
 * 只能利用构造函数注入，在调用本类实例化的地方即MVCconfig中注入RedisTemplate
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;//直接放行 此处return true是对的，若return false，第一次访问登录页面时就会被拦截；若return true，第一次访问登录页会进入Login拦截器，由于登录页为放行路径，放行
        }
        //2.基于token获取redis中的用户信息
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(key);
        //3.判断用户信息是否存在
        if(userMap.isEmpty()){
            return true;//也放行
        }
        //5.将用户信息Hash转换为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.存在则保存到ThreadLocal中
        UserHolder.saveUser(userDTO);
        //7.刷新token的redis有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.SECONDS);
        //7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

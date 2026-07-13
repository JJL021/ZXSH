package com.zxsh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zxsh.dto.Result;
import com.zxsh.dto.ScrollResult;
import com.zxsh.dto.UserDTO;
import com.zxsh.entity.Blog;
import com.zxsh.entity.Follow;
import com.zxsh.entity.User;
import com.zxsh.mapper.BlogMapper;
import com.zxsh.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zxsh.service.IFollowService;
import com.zxsh.utils.SystemConstants;
import com.zxsh.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zxsh.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.zxsh.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private UserServiceImpl userService ;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    //查询博客信息
    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //2.查询blog的用户信息
        queryBlogUser(blog);
        //3.查询blog是否本用户被点赞 用于前端高亮
        isBlogLiked(blog);
        //4.返回信息
        return Result.ok(blog);
    }
    //工具方法 判断用户是否已经点过赞
    private void isBlogLiked(Blog blog) {
        //1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //用户未登录，无需查询是否点赞
            return;
        }
        Long userID = user.getId();
        if(userID != null){
            //2.判断是否已经点赞
            String key = BLOG_LIKED_KEY +blog.getId();
            Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());
            //3.配置blog的isLiked字段
            blog.setIsLike(score != null);
        }

    }

    //查询热点博客信息
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });  //原来的lambda表达式可以这样写
        return Result.ok(records);

    }

    @Override
    public Result likeBlog(Long id) {
        //1. 获取登录用户
        Long userID = UserHolder.getUser().getId();
        //2.判断是否已经点赞
        String key = BLOG_LIKED_KEY +id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());//zset没有isMember，使用查询score代替
        if(score == null){
            //3.未点赞，则可以点赞
            //3.1更新数据库点赞数 +1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                //3.2将用户放到redis的set集合中  zadd key value score
                stringRedisTemplate.opsForZSet().add(key,userID.toString(),System.currentTimeMillis()); //将zset的score用时间戳指定
            }

        }else{
            //4.如果用户已经点赞，取消点赞
            //4.1 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                //4.2将用户从redis的集合中移除
                stringRedisTemplate.opsForZSet().remove(key,userID.toString());
            }


        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top5的点赞用户 zrange key 0 4
        String key = BLOG_LIKED_KEY +id;
        Set<String> top5Set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5Set == null || top5Set.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析其中的用户id
        List<Long> ids = top5Set.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据用户id查询用户 where id IN (5,1) ORDER BY FIELD(id,5,1)  in关键字会导致新的顺序
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids)
                .last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败!");
        }
        //3. 查询博文作者所有粉丝 select * from tb_follow where follow_user_id =？
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //4. 推送博文id给所有粉丝
        for (Follow follow : follows) {
            //4.1获取粉丝id
            Long userId = follow.getUserId();
            //4.2推送
            String key = FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1. 获取当前用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            Result.fail("请先登录！");
        }
        Long userId = user.getId();
        //2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //3.非空判断
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //4.解析数据 blogId minTime(时间戳) offset:集合里面分数值等于最小分数的个数
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String idStr = typedTuple.getValue();
            ids.add(Long.valueOf(idStr));
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = typedTuple.getScore().longValue();
                os = 0;
            }
        }
        //5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();
        //5.1.查询blog的用户信息
        for (Blog blog : blogs) {
            //查询blog相关的用户
            queryBlogUser(blog);
            //查询blog是否本用户被点赞 用于前端高亮
            isBlogLiked(blog);
        }

        //6.拼装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    //工具方法 手动补充blog中关于用户的字段
    private void queryBlogUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}

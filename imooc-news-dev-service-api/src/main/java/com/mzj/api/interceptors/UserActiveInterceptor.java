package com.mzj.api.interceptors;

import com.mzj.enums.UserStatus;
import com.mzj.exception.GraceException;
import com.mzj.grace.result.ResponseStatusEnum;
import com.mzj.pojo.AppUser;
import com.mzj.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户激活状态检查拦截器
 * 发文章，修改文章，删除文章
 * 发表评论，查看评论等等
 * 这些接口都是需要在用户激活以后，才能进行
 * 否则需要提示用户前往[账号设置]去修改信息
 */
public class UserActiveInterceptor extends BaseInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String userId = request.getHeader("headerUserId");

        String redisData = redisOperator.get(REDIS_USER_INFO + ":" + userId);
        AppUser user = null;
        if (StringUtils.isNotBlank(redisData)){
            user = JsonUtils.jsonToPojo(redisData, AppUser.class);
        }else{
            GraceException.display(ResponseStatusEnum.UN_LOGIN); // 在redis查询不到用户信息，抛出错误: 请登录后再进行操作
            return false;
        }
        if (user.getActiveStatus() == null || user.getActiveStatus() != UserStatus.ACTIVE.type){
            GraceException.display(ResponseStatusEnum.USER_INACTIVE_ERROR); // 请前往[账号设置]修改信息激活后再进行后续操作！
            return false;
        }
        /**
         * false: 请求被拦截
         * true: 请求通过验证，放行
         */
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}

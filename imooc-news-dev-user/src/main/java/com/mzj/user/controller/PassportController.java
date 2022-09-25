package com.mzj.user.controller;


import com.mzj.api.BaseController;
import com.mzj.api.controller.user.PassportControllerApi;
import com.mzj.enums.UserStatus;
import com.mzj.grace.result.GraceJSONResult;
import com.mzj.grace.result.ResponseStatusEnum;
import com.mzj.pojo.AppUser;
import com.mzj.pojo.bo.RegistLoginBO;
import com.mzj.user.service.UserService;
import com.mzj.utils.IPUtil;
import com.mzj.utils.JsonUtils;
import com.mzj.utils.RedisOperator;
import com.mzj.utils.SMSUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Map;
import java.util.UUID;


@RestController
public class PassportController extends BaseController implements PassportControllerApi {

    final static Logger logger = LoggerFactory.getLogger(PassportController.class);

    @Autowired
    private SMSUtils smsUtils;

    @Autowired
    private UserService userService;

    @Override
    public GraceJSONResult getSMSCode(String mobile, HttpServletRequest request) {

        // 获得用户ip
        String userIp = IPUtil.getRequestIp(request);

        // 根据用户的ip进行限制，限制用户在60秒内只能获得一次验证码
        redisOperator.setnx60s(MOBILE_SMSCODE+":"+userIp,userIp);
        // 生成随机验证码并且发送短信
        String random = (int)((Math.random() * 9 + 1) * 100000) + "";
        // 发送短信
        smsUtils.sendSMS(mobile,random);
        // 把验证码存入redis，用户后续进行验证
        redisOperator.set(MOBILE_SMSCODE+":"+mobile,random,30*60);

        return GraceJSONResult.ok();
    }

    @Override
    public GraceJSONResult doLogin(@Valid RegistLoginBO registLoginBO,
                                   BindingResult result,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {

        // 0.判断BindingResult中是否保存了错误的信息，如果有则需要返回
        if(result.hasErrors()){
            Map<String, String> errors = getErrors(result);
            return GraceJSONResult.errorMap(errors);
        }

        String mobile = registLoginBO.getMobile();
        String smsCode = registLoginBO.getSmsCode();
        // 1.校验验证码是否匹配
        String redisSMSCode = redisOperator.get(MOBILE_SMSCODE + ":" + mobile);
        if (StringUtils.isBlank(redisSMSCode) || !redisSMSCode.equalsIgnoreCase(smsCode)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SMS_CODE_ERROR);// 验证码过期或者不匹配!
        }

        // 2.查询数据库，判断是否注册
        AppUser appUser = userService.queryMobileIsExists(mobile);
        // 进行判断，如果appUser为空则创建一个新的用户，不为空则将
        if (appUser!=null && appUser.getActiveStatus()== UserStatus.FROZEN.type){
            // 如果用户不为空并且状态为冻结，则直接排除异常，禁止登录
            return GraceJSONResult.errorCustom(ResponseStatusEnum.USER_FROZEN); // 用户已冻结
        // 如果用户为空表示是新用户，那么我们就进行注册
        }else if (appUser==null){
            appUser = userService.createUser(mobile);
        }
        // 3.保存用户分布式会话的相关操作
        int userActiveStatus = appUser.getActiveStatus();
        if (userActiveStatus!=UserStatus.FROZEN.type){
            // 保存token到redis
            String uToken = UUID.randomUUID().toString();
            redisOperator.set(REDIS_USER_TOKEN+":"+appUser.getId(),uToken);
            redisOperator.set(REDIS_USER_INFO+":"+appUser.getId(), JsonUtils.objectToJson(appUser));

            // 保存用户id和token到cookie中
            setCookie(request,response,"utoken",uToken,COOKIE_MONTH);
            setCookie(request,response,"uid",appUser.getId(),COOKIE_MONTH);
        }

        // 4.用户登录或者注册成功后以后,需要删除redis中的短信验证码，验证码只能使用一次，用过后就进行删除
        redisOperator.del(MOBILE_SMSCODE + ":" + mobile);

        // 5.返回用户状态
        return GraceJSONResult.ok(userActiveStatus);
    }

    @Override
    public GraceJSONResult logout(String userId, HttpServletRequest request, HttpServletResponse response) {
        redisOperator.del(REDIS_USER_TOKEN+":"+userId);
        setCookie(request,response,"utoken","",COOKIE_DELETE);
        setCookie(request,response,"uid","",COOKIE_DELETE);
        return GraceJSONResult.ok();
    }
}

package com.mzj.user.controller;

import com.mzj.api.BaseController;
import com.mzj.api.controller.user.UserControllerApi;
import com.mzj.grace.result.GraceJSONResult;
import com.mzj.grace.result.ResponseStatusEnum;
import com.mzj.pojo.AppUser;
import com.mzj.pojo.bo.UpdateUserInfoBO;
import com.mzj.pojo.vo.AppUserVO;
import com.mzj.pojo.vo.UserAccountInfoVO;
import com.mzj.user.service.UserService;
import com.mzj.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

@RestController
public class UserController extends BaseController implements UserControllerApi {

    final static Logger logger = LoggerFactory.getLogger(PassportController.class);

    @Autowired
    private UserService userService;

    @Override
    public GraceJSONResult getUserInfo(String userId) {
        // 0.判断参数不能为空
        if (StringUtils.isBlank(userId)){
            return GraceJSONResult.errorCustom(ResponseStatusEnum.UN_LOGIN); // 请登录后再继续操作！
        }
        // 1.根据userId查询用户的信息
        AppUser user = getUser(userId);

        // 2.返回用户信息
        AppUserVO appUserVO = new AppUserVO();
        BeanUtils.copyProperties(user,appUserVO);

        return GraceJSONResult.ok(appUserVO);
    }

    @Override
    public GraceJSONResult getAccountInfo(String userId) {
        // 0.判断参数不能为空
        if (StringUtils.isBlank(userId)){
            return GraceJSONResult.errorCustom(ResponseStatusEnum.UN_LOGIN); // 请登录后再继续操作！
        }
        // 1.根据userId查询用户的信息
        AppUser user = getUser(userId);

        // 2.返回用户信息
        UserAccountInfoVO userAccountInfoVO = new UserAccountInfoVO();
        BeanUtils.copyProperties(user,userAccountInfoVO);

        return GraceJSONResult.ok(userAccountInfoVO);
    }

    public AppUser getUser(String userId){
        // 查询判断redis中是否包含用户信息，如果包含则查询后直接返回，就不去查询数据库了
        String redisData = redisOperator.get(REDIS_USER_INFO + ":" + userId);
        AppUser user = null;
        if (StringUtils.isNotBlank(redisData)){
            user = JsonUtils.jsonToPojo(redisData, AppUser.class);
        }else{
            user = userService.getUser(userId);
            // 由于用户信息不怎么会变动，对于一些千万级别的网站来说，这类信息不会直接查询数据库
            // 那么完全就可以以来redis，直接把查询到的数据存放到redis中

            // 将java对象转换成json(JsonUtils)
            redisOperator.set(REDIS_USER_INFO+":"+userId,JsonUtils.objectToJson(user));
        }

        return user;
    }

    @Override
    public GraceJSONResult updateUserInfo(@Valid UpdateUserInfoBO updateUserInfoBO,
                                          BindingResult result) {
        // 0.校验BO
        if(result.hasErrors()){
            Map<String, String> errors = getErrors(result);
            return GraceJSONResult.errorMap(errors);
        }
        // 1.执行更新操作
        userService.updateUserInfo(updateUserInfoBO);

        return GraceJSONResult.ok();
    }
}

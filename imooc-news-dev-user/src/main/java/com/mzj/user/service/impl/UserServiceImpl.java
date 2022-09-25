package com.mzj.user.service.impl;

import com.mzj.enums.Sex;
import com.mzj.enums.UserStatus;
import com.mzj.exception.GraceException;
import com.mzj.grace.result.ResponseStatusEnum;
import com.mzj.pojo.AppUser;
import com.mzj.pojo.bo.UpdateUserInfoBO;
import com.mzj.user.mapper.AppUserMapper;
import com.mzj.user.service.UserService;
import com.mzj.utils.DateUtil;
import com.mzj.utils.DesensitizationUtil;
import com.mzj.utils.JsonUtils;
import com.mzj.utils.RedisOperator;
import org.n3r.idworker.Sid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private AppUserMapper appUserMapper;
    @Autowired
    private Sid sid;
    @Autowired
    private RedisOperator redisOperator;

    public static final String REDIS_USER_INFO = "redis_user_info";

    private static final String USER_FACE0 = "http://122.152.205.72:88/group1/M00/00/05/CpoxxFw_8_qAIlFXAAAcIhVPdSg994.png";
    private static final String USER_FACE1 = "http://122.152.205.72:88/group1/M00/00/05/CpoxxF6ZUySASMbOAABBAXhjY0Y649.png";
    private static final String USER_FACE2 = "http://122.152.205.72:88/group1/M00/00/05/CpoxxF6ZUx6ANoEMAABTntpyjOo395.png";

    @Override
    public AppUser queryMobileIsExists(String mobile) {
        Example userExample = new Example(AppUser.class);
        Example.Criteria userCriteria = userExample.createCriteria();
        userCriteria.andEqualTo("mobile",mobile);

        AppUser appUser = appUserMapper.selectOneByExample(userExample);

        return appUser;
    }

    @Transactional
    @Override
    public AppUser createUser(String mobile) {
        /**
         * 互联网项目都要考虑可扩展性
         * 如果未来的业务激增，那么就需要分库分表
         * 那么数据库表主键id必须保证全局（全库）唯一，不得重复
         */
        String userId = sid.nextShort();

        AppUser user = new AppUser();

        user.setId(userId);
        user.setMobile(mobile);
        user.setNickname("用户：" + DesensitizationUtil.commonDisplay(mobile)); // 脱敏操作
        user.setFace(USER_FACE2);

        user.setBirthday(DateUtil.stringToDate("1900-01-01"));
        user.setSex(Sex.secret.type);
        user.setActiveStatus(UserStatus.INACTIVE.type);

        user.setTotalIncome(0);
        user.setCreatedTime(new Date());
        user.setUpdatedTime(new Date());

        appUserMapper.insert(user);

        return user;
    }

    @Override
    public AppUser getUser(String userId) {
        return appUserMapper.selectByPrimaryKey(userId);
    }

    @Override
    public void updateUserInfo(UpdateUserInfoBO updateUserInfoBO) {
        String userId = updateUserInfoBO.getId();

        // 保证双写一致，先删除redis中的数据，后更新数据库
        redisOperator.del(REDIS_USER_INFO+":"+userId);

        AppUser userInfo = new AppUser();
        BeanUtils.copyProperties(updateUserInfoBO,userInfo);

        userInfo.setUpdatedTime(new Date());
        userInfo.setActiveStatus(UserStatus.ACTIVE.type);

        int result = appUserMapper.updateByPrimaryKeySelective(userInfo);
        if (result!=1){
            GraceException.display(ResponseStatusEnum.USER_UPDATE_ERROR); // 用户信息更新错误
        }
        // 再次查询用户的最新信息，放入redis中
        AppUser user = getUser(userId);
        redisOperator.set(REDIS_USER_INFO+":"+userId, JsonUtils.objectToJson(user));

        // 缓存双删策略
        try {
            Thread.sleep(100);
            redisOperator.del(REDIS_USER_INFO+":"+userId);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

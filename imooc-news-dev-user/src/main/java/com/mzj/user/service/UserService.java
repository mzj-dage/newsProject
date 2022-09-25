package com.mzj.user.service;

import com.mzj.pojo.AppUser;
import com.mzj.pojo.bo.UpdateUserInfoBO;

public interface UserService {
    /**
     * 判断用户是否存在，如果存在返回User信息
     */
    public AppUser queryMobileIsExists(String mobile);

    /**
     * 创建/新增用户到数据库
     */
    public AppUser createUser(String mobile);

    /**
     * 根据用户主键id查询主键信息
     */
    public AppUser getUser(String userId);

    /**
     * 用户修改信息，完善资料，并且激活
     */
    public void updateUserInfo(UpdateUserInfoBO updateUserInfoBO);
}

package com.cdy.shortlink.admin.dto.req;

import lombok.Data;

/**
 * 用户修改密码请求实体
 */
@Data
public class UserChangePasswordReqDTO {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;
}

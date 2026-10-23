package com.cdy.shortlink.admin.common.enums;

import com.cdy.shortlink.admin.common.convention.errorcode.IErrorCode;

public enum UserErrorCodeEnum implements IErrorCode {

    USER_NULL("B002000", "用户记录不存在"),
    USER_NAME_EXIST("B002001", "用户名已存在"),
    USER_EXIST("B002002", "用户记录已存在"),
    USER_SAVE_ERROR("B002003", "用户记录保存失败"),
    USER_LOGIN("B002004", "用户已登录"),
    USER_NOT_LOGIN("B002005", "用户未登录");



    private final String code;

    private final String message;

    UserErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

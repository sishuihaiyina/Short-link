package com.cdy.shortlink.project.toolkit;

import cn.hutool.core.util.StrUtil;

/**
 * IP 归属地解析结果
 *
 * @param province 省份
 * @param city     城市
 * @param adcode   区域编码
 * @param resolved 是否解析成功；内网 IP、接口失败、高德返回空地区时都是 false
 */
public record IpLocale(String province, String city, String adcode, boolean resolved) {

    /**
     * 解析不出来时的占位文案
     */
    private static final String UNKNOWN_TEXT = "未知";

    /**
     * 未知结果：访问记录里显示「中国-未知-未知」，但不参与地区统计
     */
    private static final IpLocale UNKNOWN = new IpLocale(UNKNOWN_TEXT, UNKNOWN_TEXT, UNKNOWN_TEXT, false);

    public IpLocale {
        province = normalize(province);
        city = normalize(city);
        adcode = normalize(adcode);
    }

    /**
     * 解析失败 / 内网 IP 时的统一返回值
     */
    public static IpLocale unknown() {
        return UNKNOWN;
    }

    /**
     * 访问日志里存的完整地区串，形如「中国-广东省-深圳市」
     */
    public String fullLocale() {
        return "中国-" + province + "-" + city;
    }

    /**
     * 高德查不到城市时会返回 "[]"，这里统一成「未知」，避免把 "[]" 写进库
     */
    private static String normalize(String value) {
        return StrUtil.isBlank(value) || "[]".equals(value) ? UNKNOWN_TEXT : value.trim();
    }
}

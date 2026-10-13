package com.cdy.shortlink.admin.toolkit;

import java.security.SecureRandom;

/**
 * 分组ID随机数生成器
 */
public final class RandomGenerator {

    private static final String CHARACTER = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成随机分组ID
     * @return 随机分组ID
     */
    public static String generateRandom() {
        return generateRandom(16);
    }

    /**
     * 生成随机分组ID
     * @param length 长度
     * @return 随机分组ID
     */
    public static String generateRandom(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTER.length());
            sb.append(CHARACTER.charAt(randomIndex));
        }
        return sb.toString();
    }
}

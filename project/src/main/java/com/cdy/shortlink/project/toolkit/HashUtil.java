package com.cdy.shortlink.project.toolkit;

import cn.hutool.core.lang.hash.MurmurHash;

/**
 * HASH 工具类
 * <p>
 * 用于将原始链接转换为 62 进制的短链接。
 * <p>
 * 使用 64 位 MurmurHash：32 位哈希空间只有 42.9 亿，按生日悖论累计约 7.7 万条就有 50% 概率
 * 出现一次碰撞，之后只能靠布隆过滤器 + 重试兜底；换成 64 位后同样的碰撞概率要到约 50 亿条。
 * 代价是短码长度从 6 位变成最多 11 位（62^11 ≈ 5.2e19 > 2^63 ≈ 9.2e18）。
 */
public class HashUtil {

    private static final char[] CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    private static final int SIZE = CHARS.length;

    private static String convertDecToBase62(long num) {
        if (num <= 0) {
            return String.valueOf(CHARS[0]);
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            int i = (int) (num % SIZE);
            sb.append(CHARS[i]);
            num /= SIZE;
        }
        return sb.reverse().toString();
    }

    public static String hashToBase62(String str) {
        long hash = MurmurHash.hash64(str);
        // 64 位哈希可能为负，取绝对值；Long.MIN_VALUE 取反会溢出，退化为 0
        long num = hash == Long.MIN_VALUE ? 0L : Math.abs(hash);
        return convertDecToBase62(num);
    }
}

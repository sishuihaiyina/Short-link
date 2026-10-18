package com.cdy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdy.shortlink.project.dao.entity.LinkLocaleStatsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 链接区域统计Mapper
 */
public interface LinkLocaleStatsMapper extends BaseMapper<LinkLocaleStatsDO> {

    /**
     * 短链接区域统计
     * @param linkLocaleStatsDO 链接区域统计DO
     */
    @Insert("INSERT INTO t_link_locale_stats (full_short_url, gid, date, cnt, province, city, adcode, country, create_time, update_time, del_flag) " +
            "VALUES (#{linkLocaleStatsDO.fullShortUrl}, " +
            "#{linkLocaleStatsDO.gid}, " +
            "#{linkLocaleStatsDO.date}, " +
            "#{linkLocaleStatsDO.cnt}, " +
            "#{linkLocaleStatsDO.province}, " +
            "#{linkLocaleStatsDO.city}, " +
            "#{linkLocaleStatsDO.adcode}, " +
            "#{linkLocaleStatsDO.country}, " +
            "now()" +
            ", now()" +
            ", 0" +
            ") " +
            "ON DUPLICATE KEY UPDATE " +
            "cnt = cnt + #{linkLocaleStatsDO.cnt}")
    void shortLinkLocaleStats(@Param("linkLocaleStatsDO") LinkLocaleStatsDO linkLocaleStatsDO);
}

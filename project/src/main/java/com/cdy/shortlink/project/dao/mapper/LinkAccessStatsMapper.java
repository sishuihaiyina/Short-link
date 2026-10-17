package com.cdy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdy.shortlink.project.dao.entity.LinkAccessStatsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 短链接访问统计Mapper
 */
public interface LinkAccessStatsMapper extends BaseMapper<LinkAccessStatsDO> {

    /**
     * 记录短链接访问统计
     */
    @Insert("INSERT INTO t_link_access_stats (full_short_url, gid, date, uip, uv, pv, hour, weekday, create_time, update_time, del_flag) " +
            "VALUES (#{linkAccessStats.fullShortUrl}, " +
            "#{linkAccessStats.gid}, " +
            "#{linkAccessStats.date}, " +
            "#{linkAccessStats.uip}, " +
            "#{linkAccessStats.uv}, " +
            "#{linkAccessStats.pv}, " +
            "#{linkAccessStats.hour}, " +
            "#{linkAccessStats.weekday}, " +
            "now()" +
            ", now()" +
            ", 0" +
            ") " +
            "ON DUPLICATE KEY UPDATE " +
            "pv = pv + #{linkAccessStats.pv}, " +
            "uv = uv + #{linkAccessStats.uv}, " +
            "uip = uip + #{linkAccessStats.uip}")
    void shortLinkStats(@Param("linkAccessStats") LinkAccessStatsDO linkAccessStats);

}

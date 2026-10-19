package com.cdy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdy.shortlink.project.dao.entity.LinkStatsTodayDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 短链接今日统计Mapper
 */
public interface LinkStatsTodayMapper extends BaseMapper<LinkStatsTodayDO> {

    /**
     * 记录访问操作系统监控数据
     * @param linkTodayStats
     */
    @Insert("INSERT INTO t_link_stats_today (full_short_url, gid, date, today_uv, today_pv, today_uip, create_time, update_time, del_flag) " +
            "VALUES (#{linkTodayStats.fullShortUrl}, " +
            "#{linkTodayStats.gid}, " +
            "#{linkTodayStats.date}, " +
            "#{linkTodayStats.todayUv}, " +
            "#{linkTodayStats.todayPv}, " +
            "#{linkTodayStats.todayUip}, " +
            "now()" +
            ", now()" +
            ", 0" +
            ") " +
            "ON DUPLICATE KEY UPDATE " +
            "today_uv = today_uv + #{linkTodayStats.todayUv}, " +
            "today_pv = today_pv + #{linkTodayStats.todayPv}, " +
            "today_uip = today_uip + #{linkTodayStats.todayUip}")
    void shortLinkTodayStats(@Param("linkTodayStats") LinkStatsTodayDO linkTodayStats);
}

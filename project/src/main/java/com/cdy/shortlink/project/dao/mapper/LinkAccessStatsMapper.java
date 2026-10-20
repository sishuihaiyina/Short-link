package com.cdy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdy.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.cdy.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 根据短链接获取指定日期内基础监控数据
     */
    @Select("SELECT " +
            "    date, " +
            "    SUM(pv) AS pv, " +
            "    SUM(uv) AS uv, " +
            "    SUM(uip) AS uip " +
            "FROM " +
            "    t_link_access_stats " +
            "WHERE " +
            "    full_short_url = #{param.fullShortUrl} " +
            "    AND gid = #{param.gid} " +
            "    AND date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    full_short_url, gid, date;")
    List<LinkAccessStatsDO> listStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内小时基础监控数据
     */
    @Select("SELECT " +
            "    hour, " +
            "    SUM(pv) AS pv " +
            "FROM " +
            "    t_link_access_stats " +
            "WHERE " +
            "    full_short_url = #{param.fullShortUrl} " +
            "    AND gid = #{param.gid} " +
            "    AND date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    full_short_url, gid, hour;")
    List<LinkAccessStatsDO> listHourStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内小时基础监控数据
     */
    @Select("SELECT " +
            "    weekday, " +
            "    SUM(pv) AS pv " +
            "FROM " +
            "    t_link_access_stats " +
            "WHERE " +
            "    full_short_url = #{param.fullShortUrl} " +
            "    AND gid = #{param.gid} " +
            "    AND date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    full_short_url, gid, weekday;")
    List<LinkAccessStatsDO> listWeekdayStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);


}

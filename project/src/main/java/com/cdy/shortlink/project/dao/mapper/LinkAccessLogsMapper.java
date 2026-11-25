package com.cdy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdy.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.cdy.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.cdy.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 链接访问日志监控Mapper
 */
public interface LinkAccessLogsMapper extends BaseMapper<LinkAccessLogsDO> {

    /**
     * 插入访问日志，event_id 唯一索引用于重复消费的幂等去重
     * <p>
     * 必须用 INSERT IGNORE，不能用 ON DUPLICATE KEY UPDATE id = id：
     * Connector/J 默认 useAffectedRows=false（CLIENT_FOUND_ROWS），
     * 冲突时的 no-op 更新会返回 1 而不是 0，导致幂等判断失效。
     * <p>
     * create_time / update_time / del_flag 由 SQL 显式写入：
     * 自定义 @Insert 不会触发 MyBatis-Plus 的自动填充。
     *
     * @return 1：插入成功（首次消费）；0：event_id 已存在（重复消息）
     */
    @Insert("INSERT IGNORE INTO t_link_access_logs " +
            "(event_id, user, ip, browser, os, network, device, locale, gid, full_short_url, create_time, update_time, del_flag) " +
            "VALUES (#{log.eventId}, #{log.user}, #{log.ip}, #{log.browser}, #{log.os}, " +
            "#{log.network}, #{log.device}, #{log.locale}, #{log.gid}, #{log.fullShortUrl}, now(), now(), 0)")
    int insertIgnore(@Param("log") LinkAccessLogsDO log);

    /**
     * 根据短链接获取指定日期内高频访问IP数据
     */
    @Select("SELECT " +
            "    ip, " +
            "    COUNT(ip) AS count " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    full_short_url = #{param.fullShortUrl} " +
            "    AND gid = #{param.gid} " +
            "    AND create_time >= #{param.startDate} " +
            "    AND create_time < DATE_ADD(DATE(#{param.endDate}), INTERVAL 1 DAY) " +
            "GROUP BY " +
            "    full_short_url, gid, ip " +
            "ORDER BY " +
            "    count DESC " +
            "LIMIT 5;")
    List<HashMap<String, Object>> listTopIpByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内新旧访客数据
     */
    @Select("SELECT " +
            "    SUM(old_user) AS oldUserCnt, " +
            "    SUM(new_user) AS newUserCnt " +
            "FROM ( " +
            "    SELECT " +
            "        CASE WHEN COUNT(DISTINCT DATE(create_time)) > 1 THEN 1 ELSE 0 END AS old_user, " +
            "        CASE WHEN COUNT(DISTINCT DATE(create_time)) = 1 AND MAX(create_time) >= CONCAT(#{param.startDate},' 00:00:00') AND MAX(create_time) <= CONCAT(#{param.endDate},' 23:59:59') THEN 1 ELSE 0 END AS new_user " +
            "    FROM " +
            "        t_link_access_logs " +
            "    WHERE " +
            "        full_short_url = #{param.fullShortUrl} " +
            "        AND gid = #{param.gid} " +
            "    GROUP BY " +
            "        user " +
            ") AS user_counts;")
    HashMap<String, Object> findUvTypeCntByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 获取用户信息是否新老访客
     */
    @Select("<script> " +
            "SELECT " +
            "    user, " +
            "    CASE " +
            "        WHEN MIN(create_time) >= #{startDate} " +
            "             AND MIN(create_time) &lt; DATE_ADD(DATE(#{endDate}), INTERVAL 1 DAY) THEN '新访客' " +
            "        ELSE '老访客' " +
            "    END AS uvType " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    full_short_url = #{fullShortUrl} " +
            "    AND gid = #{gid} " +
            "    AND user IN " +
            "    <foreach item='item' index='index' collection='userAccessLogsList' open='(' separator=',' close=')'> " +
            "        #{item} " +
            "    </foreach> " +
            "GROUP BY " +
            "    user;" +
            "    </script>"
    )
    List<Map<String, Object>> selectUvTypeByUsers(
            @Param("gid") String gid,
            @Param("fullShortUrl") String fullShortUrl,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate, @Param("userAccessLogsList") List<String> userAccessLogsList);


    /**
     * 根据短链接获取指定日期内PV、UV、UIP数据
     */
    @Select("SELECT " +
            "    COUNT(user) AS pv, " +
            "    COUNT(DISTINCT user) AS uv, " +
            "    COUNT(DISTINCT ip) AS uip " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    full_short_url = #{param.fullShortUrl} " +
            "    AND gid = #{param.gid} " +
            "    AND create_time >= #{param.startDate} " +
            "    AND create_time < DATE_ADD(DATE(#{param.endDate}), INTERVAL 1 DAY) " +
            "GROUP BY " +
            "    full_short_url, gid;")
    LinkAccessStatsDO findPvUvUipStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内高频访问IP数据
     */
    @Select("SELECT " +
            "    ip, " +
            "    COUNT(ip) AS count " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    gid = #{param.gid} " +
            "    AND create_time >= #{param.startDate} " +
            "    AND create_time < DATE_ADD(DATE(#{param.endDate}), INTERVAL 1 DAY) " +
            "GROUP BY " +
            "    gid, ip " +
            "ORDER BY " +
            "    count DESC " +
            "LIMIT 5;")
    List<HashMap<String, Object>> listTopIpByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内PV、UV、UIP数据
     */
    @Select("SELECT " +
            "    COUNT(user) AS pv, " +
            "    COUNT(DISTINCT user) AS uv, " +
            "    COUNT(DISTINCT ip) AS uip " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    gid = #{param.gid} " +
            "    AND create_time >= #{param.startDate} " +
            "    AND create_time < DATE_ADD(DATE(#{param.endDate}), INTERVAL 1 DAY) " +
            "GROUP BY " +
            "    gid;")
    LinkAccessStatsDO findPvUvUidStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);

    /**
     * 获取分组内用户信息是否新老访客
     */
    @Select("<script> " +
            "SELECT " +
            "    user, " +
            "    CASE " +
            "        WHEN MIN(create_time) >= #{startDate} " +
            "             AND MIN(create_time) &lt; DATE_ADD(DATE(#{endDate}), INTERVAL 1 DAY) THEN '新访客' " +
            "        ELSE '老访客' " +
            "    END AS uvType " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "   gid = #{gid} " +
            "    AND user IN " +
            "    <foreach item='item' index='index' collection='userAccessLogsList' open='(' separator=',' close=')'> " +
            "        #{item} " +
            "    </foreach> " +
            "GROUP BY " +
            "    user;" +
            "    </script>"
    )
    List<Map<String, Object>> selectGroupUvTypeByUsers(
            @Param("gid") String gid,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("userAccessLogsList") List<String> userAccessLogsList);
}

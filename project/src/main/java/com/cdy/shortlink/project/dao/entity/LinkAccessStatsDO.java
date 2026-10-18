package com.cdy.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cdy.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接访问统计实体
 */
@Data
@TableName("t_link_access_stats")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkAccessStatsDO extends BaseDO {

    /**
     * 短链接ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 链接标识
     */
    private String gid;

    /**
     * 访问日期
     */
    private Date date;

    /**
     * 访问IP数
     */
    private Integer uip;

    /**
     * 访问用户数
     */
    private Integer uv;

    /**
     * 访问量
     */
    private Integer pv;

    /**
     * 访问小时
     */
    private Integer hour;

    /**
     * 访问周
     */
    private Integer weekday;

}

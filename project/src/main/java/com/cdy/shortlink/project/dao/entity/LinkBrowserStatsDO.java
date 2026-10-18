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
 * 短链接浏览器统计
 */
@Data
@TableName("link_browser_stats")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkBrowserStatsDO extends BaseDO {

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
     * 访问量
     */
    private Integer cnt;

    /**
     * 访问浏览器
     */
    private String browser;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 删除标志 0：未删除 1：已删除
     */
    private Integer delFlag;
}

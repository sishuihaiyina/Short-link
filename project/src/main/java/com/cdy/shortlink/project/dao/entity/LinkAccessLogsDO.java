package com.cdy.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cdy.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链接访问日志监控实体
 */
@Data
@TableName("t_link_access_logs")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkAccessLogsDO extends BaseDO {

    /**
     * 短链接ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 访问事件 ID（全局唯一，消费端幂等去重）
     */
    private String eventId;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 链接标识
     */
    private String gid;

    /**
     * 访问用户
     */
    private String user;

    /**
     * 访问IP
     */
    private String ip;

    /**
     * 访问操作系统
     */
    private String os;

    /**
     * 访问浏览器
     */
    private String browser;

    /**
     * 访问网络
     */
    private String network;

    /**
     * 访问设备
     */
    private String device;

    /**
     * 访问地区
     */
    private String locale;

}

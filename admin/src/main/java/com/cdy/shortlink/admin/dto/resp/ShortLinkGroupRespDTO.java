package com.cdy.shortlink.admin.dto.resp;

import lombok.Data;

/**
 * 短连接分组返回实体
 */
@Data
public class ShortLinkGroupRespDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 群组名称
     */
    private String name;

    /**
     * 用户名
     */
    private String username;

    /**
     * 排序字段
     */
    private Integer sortOrder;
}

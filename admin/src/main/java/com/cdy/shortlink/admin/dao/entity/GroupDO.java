package com.cdy.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cdy.shortlink.admin.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短连接分组实体
 */
@Data
@TableName("t_group")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

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

package com.cdy.shortlink.admin.remote.dto.req;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * URL 回收站分页请求DTO
 */
@Data
public class ShortLinkRecycleBinPageReqDTO extends Page {

    /**
     * 分组ID列表
     */
    private List<String> gidList;
}

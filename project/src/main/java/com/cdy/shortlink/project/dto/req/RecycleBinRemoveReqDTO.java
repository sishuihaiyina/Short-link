package com.cdy.shortlink.project.dto.req;

import lombok.Data;

/**
 * 回收站删除请求DTO
 */
@Data
public class RecycleBinRemoveReqDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 実整短链接
     */
    private String fullShortUrl;
}

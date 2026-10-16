package com.cdy.shortlink.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

/**
 * URL 回收站服务层接口
 */
public interface RecycleBinService {

    /**
     * 回收站分页
     * @param recycleBinPageReqDTO 回收站分页请求DTO
     * @return 回收站分页响应DTO
     */
    Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO);
}

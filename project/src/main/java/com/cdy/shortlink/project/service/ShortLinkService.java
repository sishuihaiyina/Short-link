package com.cdy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cdy.shortlink.project.dao.entity.ShortLinkDO;
import com.cdy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 短链接接口层
 */
public interface ShortLinkService extends IService<ShortLinkDO> {

    /**
     * 创建短链接
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接创建响应DTO
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO shortLinkCreateReqDTO);

    /**
     * 短链接分页
     * @param shortLinkPageReqDTO 短链接分页请求DTO
     * @return 短链接分页响应DTO
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO shortLinkPageReqDTO);
}

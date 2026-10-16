package com.cdy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cdy.shortlink.project.dao.entity.ShortLinkDO;
import com.cdy.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.cdy.shortlink.project.dto.req.RecycleBinRemoveReqDTO;
import com.cdy.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站服务层接口
 */
public interface RecycleBinService extends IService<ShortLinkDO> {

    /**
     * 回收站保存
     * @param recycleBinSaveReqDTO 回收站保存请求DTO
     */
    void saveRecycleBin(RecycleBinSaveReqDTO recycleBinSaveReqDTO);

    /**
     * 回收站分页
     * @param recycleBinPageReqDTO 回收站分页请求DTO
     * @return 回收站分页响应DTO
     */
    IPage<ShortLinkPageRespDTO> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO);

    /**
     * 回收站恢复
     * @param recycleBinRecoverReqDTO 回收站恢复请求DTO
     */
    void recoverRecycleBin(RecycleBinRecoverReqDTO recycleBinRecoverReqDTO);

    /**
     * 回收站删除
     * @param recycleBinRemoveReqDTO 回收站删除请求DTO
     */
    void removeRecycleBin(RecycleBinRemoveReqDTO recycleBinRemoveReqDTO);
}

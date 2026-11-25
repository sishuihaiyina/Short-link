package com.cdy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cdy.shortlink.project.dao.entity.LinkDlqMessageDO;

/**
 * 统计消息死信服务
 */
public interface DlqService {

    /**
     * 死信分页
     */
    IPage<LinkDlqMessageDO> pageDlq(long current, long size);

    /**
     * 重放一条死信消息
     */
    void replay(Long id);
}

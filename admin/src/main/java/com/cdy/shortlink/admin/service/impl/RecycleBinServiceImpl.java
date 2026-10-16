package com.cdy.shortlink.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdy.shortlink.admin.common.biz.user.UserContext;
import com.cdy.shortlink.admin.common.convention.exception.ServiceException;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.dao.entity.GroupDO;
import com.cdy.shortlink.admin.dao.mapper.GroupMapper;
import com.cdy.shortlink.admin.remote.ShortLinkRemoteService;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * URL 回收站服务层实现类
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final GroupMapper groupMapper;

    /**
     * 后续重构为SpringCloud feign调用
     */
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };


    /**
     * 回收站分页
     * @param recycleBinPageReqDTO 回收站分页请求DTO
     * @return 回收站分页响应DTO
     */
    @Override
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO) {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0);
        List<GroupDO> groupDOList = groupMapper.selectList(queryWrapper);
        if (CollUtil.isEmpty(groupDOList)) {
            throw new ServiceException("用户无分组信息");
        }
        recycleBinPageReqDTO.setGidList(groupDOList.stream().map(GroupDO::getGid).toList());
        return shortLinkRemoteService.pageRecycleBin(recycleBinPageReqDTO);
    }

}

package com.cdy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cdy.shortlink.admin.dao.entity.GroupDO;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.cdy.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * 短连接分组服务接口
 */
public interface GroupService extends IService<GroupDO> {

    /**
     * 新增短连接分组
     * @param groupName 分组名称
     */
    void saveGroup(String groupName);

    /**
     * 新增短连接分组
     * @param username 用户名
     * @param groupName 分组名称
     */
    void saveGroup(String username,String groupName);

    /**
     * 校验分组是否属于当前登录用户，不属于时抛出无权限异常
     * @param gid 分组标识
     */
    void checkGidOwner(String gid);

    /**
     * 查询短连接分组
     * @return 分组列表
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 修改短连接分组
     * @param shortLinkGroupUpdateReqDTO 分组修改请求参数
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO shortLinkGroupUpdateReqDTO);

    /**
     * 删除短连接分组
     * @param gid 分组标识
     */
    void deleteGroup(String gid);

    /**
     * 排序短连接分组
     * @param shortLinkGroupSortReqDTOList 分组排序请求参数列表
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> shortLinkGroupSortReqDTOList);
}

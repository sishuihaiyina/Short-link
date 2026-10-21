package com.cdy.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.admin.common.biz.user.UserContext;
import com.cdy.shortlink.admin.common.constant.RedisCacheConstant;
import com.cdy.shortlink.admin.common.convention.exception.ClientException;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.dao.entity.GroupDO;
import com.cdy.shortlink.admin.dao.mapper.GroupMapper;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.cdy.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.cdy.shortlink.admin.remote.ShortLinkRemoteService;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import com.cdy.shortlink.admin.toolkit.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 短连接分组服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final RedissonClient redissonClient;

    @Value("${short-link.group.max-number}")
    private Integer maxNumber;

    /**
     * 后续重构为SpringCloud feign调用
     */
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };


    /**
     * 新增短连接分组
     * @param groupName 分组名称
     */
    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(),groupName);
    }

    /**
     * 新增短连接分组
     * @param username 用户名
     * @param groupName 分组名称
     */
    @Override
    public void saveGroup(String username,String groupName) {
        // 引入分布式锁防止一个用户同时创建超过最大分组数量
        RLock lock = redissonClient.getLock(String.format(RedisCacheConstant.LOCK_GROUP_CREATE_KEY, username));
        lock.lock();
        try {
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUsername, username)
                    .eq(GroupDO::getDelFlag, 0);
            List<GroupDO> groupList = baseMapper.selectList(queryWrapper);
            if (CollUtil.isNotEmpty(groupList) && groupList.size() == maxNumber) {
                throw new ClientException(String.format("用户已创建最大分组数量：%d",maxNumber));
            }
            String gid;
            do {
                gid = RandomGenerator.generateRandom();
            } while (!hasGid(username, gid));

            GroupDO groupDO = GroupDO.builder()
                    .gid(gid)
                    .sortOrder(0)       // 排序字段默认值为0
                    .username(username)
                    .name(groupName)
                    .build();
            baseMapper.insert(groupDO);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 校验分组ID是否存在
     * @param username 用户名
     * @param gid 分组标识
     * @return 是否存在
     */
    private boolean hasGid(String username, String gid) {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, Optional.ofNullable(username).orElse(UserContext.getUsername()));
        GroupDO hasGroupFlag = baseMapper.selectOne(queryWrapper);
        return hasGroupFlag == null;  // true 表示不存在
    }

    /**
     * 查询短连接分组
     * @return 分组列表
     */
    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByDesc(GroupDO::getSortOrder, GroupDO::getUpdateTime);
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult =
                shortLinkRemoteService.listGroupShortLinkCount(groupDOList.stream().map(GroupDO::getGid).toList());
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
        shortLinkGroupRespDTOList.forEach(each -> {
            Optional<ShortLinkGroupCountQueryRespDTO> first = listResult.getData().stream()
                    .filter(item -> Objects.equals(item.getGid(), each.getGid()))
                    .findFirst();
            first.ifPresent(item -> each.setShortLinkCount(first.get().getShortLinkCount()));
        });
        return shortLinkGroupRespDTOList;
    }

    /**
     * 修改短连接分组
     * @param shortLinkGroupUpdateReqDTO 分组修改请求参数
     */
    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO shortLinkGroupUpdateReqDTO) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, shortLinkGroupUpdateReqDTO.getGid());
        GroupDO groupDO = new GroupDO();
        groupDO.setName(shortLinkGroupUpdateReqDTO.getName());
        baseMapper.update(groupDO, updateWrapper);
    }

    /**
     * 删除短连接分组
     * @param gid 分组标识
     */
    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);
        baseMapper.update(groupDO, updateWrapper);
    }

    /**
     * 排序短连接分组
     * @param shortLinkGroupSortReqDTOList 分组排序请求参数列表
     */
    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> shortLinkGroupSortReqDTOList) {
        shortLinkGroupSortReqDTOList.forEach(each -> {
            GroupDO groupDO = GroupDO.builder()
                    .gid(each.getGid())
                    .sortOrder(each.getSortOrder())
                    .build();
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getDelFlag, 0)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid());
            baseMapper.update(groupDO, updateWrapper);
        });
    }
}

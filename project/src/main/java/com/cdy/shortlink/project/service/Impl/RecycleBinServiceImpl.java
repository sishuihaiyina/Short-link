package com.cdy.shortlink.project.service.Impl;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.project.common.constant.RedisKeyConstant;
import com.cdy.shortlink.project.dao.entity.ShortLinkDO;
import com.cdy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.cdy.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.cdy.shortlink.project.dto.req.RecycleBinRemoveReqDTO;
import com.cdy.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.project.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 回收站服务层实现类
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 回收站保存
     * @param recycleBinSaveReqDTO 回收站保存请求DTO
     */
    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO recycleBinSaveReqDTO) {
        // 修改短链接标识为未启用标识
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, recycleBinSaveReqDTO.getFullShortUrl())
                .eq(ShortLinkDO::getGid, recycleBinSaveReqDTO.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                        .enableStatus(1)
                        .build();
        baseMapper.update(shortLinkDO, updateWrapper);
        // 删除缓存
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, recycleBinSaveReqDTO.getFullShortUrl()));
    }

    /**
     * 回收站分页
     * @param recycleBinPageReqDTO 回收站分页请求DTO
     * @return 回收站分页响应DTO
     */
    @Override
    public IPage<ShortLinkPageRespDTO> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .in(ShortLinkDO::getGid, recycleBinPageReqDTO.getGidList())
                .eq(ShortLinkDO::getEnableStatus, 1)
                .eq(ShortLinkDO::getDelFlag, 0)
                .orderByDesc(ShortLinkDO::getUpdateTime);
        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(recycleBinPageReqDTO, queryWrapper);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            // 协议暂时先写死
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }

    /**
     * 回收站恢复
     * @param recycleBinRecoverReqDTO 回收站恢复请求DTO
     */
    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO recycleBinRecoverReqDTO) {
        // 修改短链接标识为已启用标识
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, recycleBinRecoverReqDTO.getFullShortUrl())
                .eq(ShortLinkDO::getGid, recycleBinRecoverReqDTO.getGid())
                .eq(ShortLinkDO::getEnableStatus, 1)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                        .enableStatus(0)
                        .build();
        baseMapper.update(shortLinkDO, updateWrapper);
        // 删除空白的缓存
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, recycleBinRecoverReqDTO.getFullShortUrl()));

    }

    /**
     * 回收站删除
     * @param recycleBinRemoveReqDTO 回收站删除请求DTO
     */
    @Override
    public void removeRecycleBin(RecycleBinRemoveReqDTO recycleBinRemoveReqDTO) {
        // 修改短链接标识为已删除标识
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, recycleBinRemoveReqDTO.getFullShortUrl())
                .eq(ShortLinkDO::getGid, recycleBinRemoveReqDTO.getGid())
                .eq(ShortLinkDO::getEnableStatus, 1)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO delShortLinkDO  = ShortLinkDO.builder()
                        .delTime(System.currentTimeMillis())
                        .build();
        delShortLinkDO .setDelFlag(1);
        baseMapper.update(delShortLinkDO , updateWrapper);
        // 删除缓存
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, recycleBinRemoveReqDTO.getFullShortUrl()));
    }


}

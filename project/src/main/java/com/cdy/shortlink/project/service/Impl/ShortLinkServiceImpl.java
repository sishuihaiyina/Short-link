package com.cdy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.project.common.convention.exception.ServiceException;
import com.cdy.shortlink.project.dao.entity.ShortLinkDO;
import com.cdy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.cdy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.project.service.ShortLinkService;
import com.cdy.shortlink.project.toolkit.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 短链接服务层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateRegisterCachePenetrationBloomFilter;


    /**
     * 创建短链接
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接创建响应DTO
     */
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO shortLinkCreateReqDTO) {
        // shortUri数据库表使用utf8_bin,避免大小写查询到同一个短链接
        // 生成后缀
        String shortLinkSuffix = generateSuffix(shortLinkCreateReqDTO);
        // 生成完整短链接
        String fullShortUrl = shortLinkCreateReqDTO.getDomain() + "/" + shortLinkSuffix;
        // 构建短链接DO
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(shortLinkCreateReqDTO.getDomain())
                .originUrl(shortLinkCreateReqDTO.getOriginUrl())
                .gid(shortLinkCreateReqDTO.getGid())
                .createdType(shortLinkCreateReqDTO.getCreatedType())
                .validDateType(shortLinkCreateReqDTO.getValidDateType())
                .validDate(shortLinkCreateReqDTO.getValidDate())
                .describe(shortLinkCreateReqDTO.getDescribe())
                .shortUri(shortLinkSuffix)
                .fullShortUrl(fullShortUrl)
                .enableStatus(0)
                .build();
        // 布隆过滤器误判，数据库兜底
        try {
            baseMapper.insert(shortLinkDO);
        } catch (DuplicateKeyException ex) {
            // 已经误判的短链接如何处理
            // 1. 短链接确实存在
            // 2. 短链接不在缓存中
            // 完整短链查数据库
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
            if (hasShortLinkDO != null) {
                log.warn("短链接：{} 重复入库", fullShortUrl);
                throw new ServiceException("短链接生成重复");
            }
        }
        shortUriCreateRegisterCachePenetrationBloomFilter.add(fullShortUrl);

        return ShortLinkCreateRespDTO.builder()
                .gid(shortLinkCreateReqDTO.getGid())
                .originUrl(shortLinkCreateReqDTO.getOriginUrl())
                .fullShortUrl(shortLinkDO.getFullShortUrl())
                .build();
    }

    /**
     * 生成短链接后缀
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接后缀
     */
    private String generateSuffix(ShortLinkCreateReqDTO shortLinkCreateReqDTO) {
        // 哈希冲突处理，避免重复生成相同的短链接后缀(重试)
        int customGenerateCount = 0;   // 自定义生成次数
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后重试");
            }
            String originUrl = shortLinkCreateReqDTO.getOriginUrl();
            originUrl += System.currentTimeMillis();
            shortUri = HashUtil.hashToBase62(originUrl);
            // 布隆过滤器判断是否存在短链接后缀
            if (!shortUriCreateRegisterCachePenetrationBloomFilter.contains(shortLinkCreateReqDTO.getDomain() + "/" + shortUri)) {
                break;
            }
             customGenerateCount++;
        }
        return shortUri;
    }

    /**
     * 短链接分页
     * @param shortLinkPageReqDTO 短链接分页请求DTO
     * @return 短链接分页响应DTO
     */
    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO shortLinkPageReqDTO) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, shortLinkPageReqDTO.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(shortLinkPageReqDTO, queryWrapper);
        return resultPage.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
    }
}

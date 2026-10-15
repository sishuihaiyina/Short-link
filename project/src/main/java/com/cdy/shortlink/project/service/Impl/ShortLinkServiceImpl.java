package com.cdy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.project.common.convention.exception.ClientException;
import com.cdy.shortlink.project.common.convention.exception.ServiceException;
import com.cdy.shortlink.project.common.enums.VailDateTypeEnum;
import com.cdy.shortlink.project.dao.entity.ShortLinkDO;
import com.cdy.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.cdy.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.cdy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.cdy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.project.service.ShortLinkService;
import com.cdy.shortlink.project.toolkit.HashUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 短链接服务层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateRegisterCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;


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
            // 插入短链接跳转记录
            shortLinkGotoMapper.insert(ShortLinkGotoDO.builder()
                    .gid(shortLinkCreateReqDTO.getGid())
                    .fullShortUrl(fullShortUrl)
                    .build());
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
                // 协议暂时先写死
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
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
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            // 协议暂时先写死
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }

    /**
     * 查询短连接分组内数量
     * @param requestIds 短链接请求ID列表
     * @return 短链接分组内数量列表
     */
    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestIds) {
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid as gid, count(*) as shortLinkCount")
                .in("gid", requestIds)
                .eq("enable_status", 0)
                .groupBy("gid");
        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(queryWrapper);
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }

    /**
     * 修改短链接
     * @param shortLinkUpdateReqDTO 短链接修改请求DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO shortLinkUpdateReqDTO) {
        // 查询短链接是否存在
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, shortLinkUpdateReqDTO.getGid())
                .eq(ShortLinkDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
        if (hasShortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(hasShortLinkDO.getDomain())
                .shortUri(hasShortLinkDO.getShortUri())
                .clickNum(hasShortLinkDO.getClickNum())
                .favicon(hasShortLinkDO.getFavicon())
                .createdType(hasShortLinkDO.getCreatedType())
                .gid(shortLinkUpdateReqDTO.getGid())
                .originUrl(shortLinkUpdateReqDTO.getOriginUrl())
                .describe(shortLinkUpdateReqDTO.getDescribe())
                .validDate(shortLinkUpdateReqDTO.getValidDate())
                .validDateType(shortLinkUpdateReqDTO.getValidDateType())
                .build();
        // 判断是否换了分组
        if (Objects.equals(hasShortLinkDO.getGid(), shortLinkUpdateReqDTO.getGid())) {
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, shortLinkUpdateReqDTO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .set(Objects.equals(shortLinkUpdateReqDTO.getValidDateType(), VailDateTypeEnum.PERMANENT.getType()), ShortLinkDO::getValidDate, null);
            baseMapper.update(shortLinkDO, updateWrapper);
        } else {
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, hasShortLinkDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .eq(ShortLinkDO::getDelFlag, 0);
            baseMapper.delete(updateWrapper);
            shortLinkDO.setGid(shortLinkUpdateReqDTO.getGid());
            baseMapper.insert(shortLinkDO);
        }
    }

    /**
     * 短链接跳转
     * @param shortUri 短链接
     * @param response 响应
     * @param request 请求
     */
    @Override
    public void restoreUrl(String shortUri, ServletResponse response, ServletRequest request) throws IOException {
        String serverName = request.getServerName();
        String fullShortUrl = serverName + "/" + shortUri;
        LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
        ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
        if (shortLinkGotoDO == null) {
            // 严谨来说，此处应该需要封控
            return;
        }
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, shortLinkGotoDO.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
        ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
        if (shortLinkDO != null) {
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
        }
    }


}

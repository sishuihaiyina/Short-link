package com.cdy.shortlink.project.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdy.shortlink.project.common.convention.exception.ClientException;
import com.cdy.shortlink.project.dao.entity.LinkDlqMessageDO;
import com.cdy.shortlink.project.dao.mapper.LinkDlqMessageMapper;
import com.cdy.shortlink.project.mq.producer.ShortLinkStatsSaveProducer;
import com.cdy.shortlink.project.service.DlqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 统计消息死信服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqServiceImpl implements DlqService {

    private final LinkDlqMessageMapper linkDlqMessageMapper;
    private final ShortLinkStatsSaveProducer shortLinkStatsSaveProducer;

    @Override
    public IPage<LinkDlqMessageDO> pageDlq(long current, long size) {
        Page<LinkDlqMessageDO> page = new Page<>(current, size);
        return linkDlqMessageMapper.selectPage(page,
                Wrappers.lambdaQuery(LinkDlqMessageDO.class).orderByDesc(LinkDlqMessageDO::getId));
    }

    @Override
    public void replay(Long id) {
        LinkDlqMessageDO record = linkDlqMessageMapper.selectById(id);
        if (record == null) {
            throw new ClientException("死信消息不存在");
        }
        if (record.getReplayed() != null && record.getReplayed() == 1) {
            throw new ClientException("该死信消息已经重放过，请勿重复操作");
        }
        Map<String, String> producerMap;
        try {
            producerMap = JSON.parseObject(record.getPayload(), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            throw new ClientException("死信消息体无法解析，不能重放");
        }
        if (producerMap == null || producerMap.isEmpty()) {
            throw new ClientException("死信消息体为空，不能重放");
        }
        shortLinkStatsSaveProducer.send(producerMap);
        LinkDlqMessageDO update = new LinkDlqMessageDO();
        update.setId(id);
        update.setReplayed(1);
        linkDlqMessageMapper.updateById(update);
        log.info("死信消息已重放，id：{}，fullShortUrl：{}", id, record.getFullShortUrl());
    }
}

package com.cdy.shortlink.project.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.project.dao.entity.LinkStatsTodayDO;
import com.cdy.shortlink.project.dao.mapper.LinkStatsTodayMapper;
import com.cdy.shortlink.project.service.LinkStatsTodayService;
import org.springframework.stereotype.Service;

/**
 * 短链接今日统计接口实现层
 */
@Service
public class LinkStatsTodayServiceImpl extends ServiceImpl<LinkStatsTodayMapper, LinkStatsTodayDO> implements LinkStatsTodayService {
}

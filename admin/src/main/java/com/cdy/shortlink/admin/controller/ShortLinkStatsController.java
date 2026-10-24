package com.cdy.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkGroupStatsReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkStatsReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接监控控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final GroupService groupService;

    /**
     * 访问单个短链接指定时间内监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        // 只能查看自己名下的短链接监控数据
        groupService.checkGidOwner(requestParam.getGid());
        return shortLinkActualRemoteService.oneShortLinkStats(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record")
    public Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO shortLinkStatsAccessRecordReqDTO) {
        groupService.checkGidOwner(shortLinkStatsAccessRecordReqDTO.getGid());
        return shortLinkActualRemoteService.shortLinkStatsAccessRecord(
                shortLinkStatsAccessRecordReqDTO.getFullShortUrl(),
                shortLinkStatsAccessRecordReqDTO.getGid(),
                shortLinkStatsAccessRecordReqDTO.getStartDate(),
                shortLinkStatsAccessRecordReqDTO.getEndDate()
        );
    }

    /**
     * 访问分组短链接指定时间内监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        groupService.checkGidOwner(requestParam.getGid());
        return shortLinkActualRemoteService.groupShortLinkStats(
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * 访问分组短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record/group")
    public Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO shortLinkGroupStatsAccessRecordReqDTO) {
        groupService.checkGidOwner(shortLinkGroupStatsAccessRecordReqDTO.getGid());
        return shortLinkActualRemoteService.groupShortLinkStatsAccessRecord(
                shortLinkGroupStatsAccessRecordReqDTO.getGid(),
                shortLinkGroupStatsAccessRecordReqDTO.getStartDate(),
                shortLinkGroupStatsAccessRecordReqDTO.getEndDate()
        );
    }
}

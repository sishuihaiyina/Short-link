package com.cdy.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.common.convention.result.Results;
import com.cdy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkBatchCreateReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkBaseInfoRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkBatchCreateRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import com.cdy.shortlink.admin.toolkit.EasyExcelWebUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 短链接后管控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final GroupService groupService;

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO shortLinkCreateReqDTO) {
        // 只能在自己名下的分组中创建短链接
        groupService.checkGidOwner(shortLinkCreateReqDTO.getGid());
        return shortLinkActualRemoteService.createShortLink(shortLinkCreateReqDTO);
    }

    /**
     * 短链接分页
     */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO shortLinkPageReqDTO) {
        // 只能分页查询自己名下的分组
        groupService.checkGidOwner(shortLinkPageReqDTO.getGid());
        return shortLinkActualRemoteService.pageShortLink(
                shortLinkPageReqDTO.getGid(),
                shortLinkPageReqDTO.getOrderTag(),
                shortLinkPageReqDTO.getCurrent(),
                shortLinkPageReqDTO.getSize()
        );
    }

    /**
     * 修改短链接
     */
    @PostMapping("/api/short-link/admin/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO shortLinkUpdateReqDTO) {
        // 原分组和新分组都必须属于当前用户，防止把别人的短链改走或把短链改到别人的分组
        groupService.checkGidOwner(shortLinkUpdateReqDTO.getOriginGid());
        if (StrUtil.isNotBlank(shortLinkUpdateReqDTO.getGid())) {
            groupService.checkGidOwner(shortLinkUpdateReqDTO.getGid());
        }
        shortLinkActualRemoteService.updateShortLink(shortLinkUpdateReqDTO);
        return Results.success();
    }

    /**
     * 批量创建短链接
     */
    @SneakyThrows
    @PostMapping("/api/short-link/admin/v1/create/batch")
    public void batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam, HttpServletResponse response) {
        groupService.checkGidOwner(requestParam.getGid());
        Result<ShortLinkBatchCreateRespDTO> shortLinkBatchCreateRespDTOResult = shortLinkActualRemoteService.batchCreateShortLink(requestParam);
        if (shortLinkBatchCreateRespDTOResult.isSuccess()) {
            List<ShortLinkBaseInfoRespDTO> baseLinkInfos = shortLinkBatchCreateRespDTOResult.getData().getBaseLinkInfos();
            EasyExcelWebUtil.write(response, "批量创建短链接-SaaS短链接系统", ShortLinkBaseInfoRespDTO.class, baseLinkInfos);
        }
    }
}

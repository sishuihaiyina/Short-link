package com.cdy.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.common.convention.result.Results;
import com.cdy.shortlink.admin.dto.req.RecycleBinRecoverReqDTO;
import com.cdy.shortlink.admin.dto.req.RecycleBinRemoveReqDTO;
import com.cdy.shortlink.admin.dto.req.RecycleBinSaveReqDTO;
import com.cdy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import com.cdy.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站控制层
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final GroupService groupService;

    /**
     * 回收站保存
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO recycleBinSaveReqDTO) {
        groupService.checkGidOwner(recycleBinSaveReqDTO.getGid());
        shortLinkActualRemoteService.saveRecycleBin(recycleBinSaveReqDTO);
        return Results.success();
    }

    /**
     * 回收站分页
     */
    @GetMapping("/api/short-link/admin/v1/recycle-bin/page")
    public Result<Page<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO) {
        return recycleBinService.pageRecycleBin(recycleBinPageReqDTO);
    }

    /**
     * 回收站恢复
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/recover")
    public Result<Void> recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO recycleBinRecoverReqDTO) {
        groupService.checkGidOwner(recycleBinRecoverReqDTO.getGid());
        shortLinkActualRemoteService.recoverRecycleBin(recycleBinRecoverReqDTO);
        return Results.success();
    }

    /**
     * 回收站删除
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/remove")
    public Result<Void> removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO recycleBinRemoveReqDTO) {
        groupService.checkGidOwner(recycleBinRemoveReqDTO.getGid());
        shortLinkActualRemoteService.removeRecycleBin(recycleBinRemoveReqDTO);
        return Results.success();
    }

}

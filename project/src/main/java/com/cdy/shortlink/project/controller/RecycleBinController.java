package com.cdy.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cdy.shortlink.project.common.convention.result.Result;
import com.cdy.shortlink.project.common.convention.result.Results;
import com.cdy.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.cdy.shortlink.project.dto.req.RecycleBinRemoveReqDTO;
import com.cdy.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.cdy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.cdy.shortlink.project.service.RecycleBinService;
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

    /**
     * 回收站保存
     */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO recycleBinSaveReqDTO) {
        recycleBinService.saveRecycleBin(recycleBinSaveReqDTO);
        return Results.success(null);
    }

    /**
     * 回收站分页
     */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO) {
        return Results.success(recycleBinService.pageRecycleBin(recycleBinPageReqDTO));
    }

    /**
     * 回收站恢复
     */
    @PostMapping("/api/short-link/v1/recycle-bin/recover")
    public Result<Void> recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO recycleBinRecoverReqDTO) {
        recycleBinService.recoverRecycleBin(recycleBinRecoverReqDTO);
        return Results.success();
    }

    /**
     * 回收站删除
     */
    @PostMapping("/api/short-link/v1/recycle-bin/remove")
    public Result<Void> removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO recycleBinRemoveReqDTO) {
        recycleBinService.removeRecycleBin(recycleBinRemoveReqDTO);
        return Results.success();
    }
}

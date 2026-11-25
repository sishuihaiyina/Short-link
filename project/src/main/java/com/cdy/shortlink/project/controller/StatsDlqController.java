package com.cdy.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cdy.shortlink.project.common.convention.result.Result;
import com.cdy.shortlink.project.common.convention.result.Results;
import com.cdy.shortlink.project.dao.entity.LinkDlqMessageDO;
import com.cdy.shortlink.project.service.DlqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计消息死信控制层
 */
@RestController
@RequiredArgsConstructor
public class StatsDlqController {

    private final DlqService dlqService;

    /**
     * 死信分页
     */
    @GetMapping("/api/short-link/v1/stats/dlq")
    public Result<IPage<LinkDlqMessageDO>> pageDlq(@RequestParam(value = "current", defaultValue = "1") long current,
                                                   @RequestParam(value = "size", defaultValue = "10") long size) {
        return Results.success(dlqService.pageDlq(current, size));
    }

    /**
     * 重放一条死信消息
     */
    @PostMapping("/api/short-link/v1/stats/dlq/replay")
    public Result<Void> replay(@RequestParam("id") Long id) {
        dlqService.replay(id);
        return Results.success();
    }
}

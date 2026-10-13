package com.cdy.shortlink.admin.controller;

import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.common.convention.result.Results;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.cdy.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.cdy.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短连接分组控制层
 */
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * 新增短连接分组
     * @param shortLinkGroupSaveReqDTO 分组请求参数
     */
    @PostMapping("/api/short-link/v1/group")
    public Result<Void> save(@RequestBody ShortLinkGroupSaveReqDTO shortLinkGroupSaveReqDTO) {
        groupService.saveGroup(shortLinkGroupSaveReqDTO.getName());
        return Results.success();
    }

    /**
     * 查询短连接分组
     * @return 分组列表
     */
    @GetMapping("/api/short-link/v1/group")
    public Result<List<ShortLinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }

    /**
     * 修改短连接分组
     * @param shortLinkGroupUpdateReqDTO 分组修改请求参数
     */
    @PutMapping("/api/short-link/v1/group")
    public Result<Void> updateGroup(@RequestBody ShortLinkGroupUpdateReqDTO shortLinkGroupUpdateReqDTO) {
        groupService.updateGroup(shortLinkGroupUpdateReqDTO);
        return Results.success();
    }

    /**
     * 删除短连接分组
     * @param gid 分组标识
     */
    @DeleteMapping("/api/short-link/v1/group")
    public Result<Void> deleteGroup(@RequestParam String gid) {
        groupService.deleteGroup(gid);
        return Results.success();
    }

    /**
     * 排序短连接分组
     * @param shortLinkGroupSortReqDTOList 分组排序请求参数列表
     */
    @PostMapping("/api/short-link/v1/group/sort")
    public Result<Void> sortGroup(@RequestBody List<ShortLinkGroupSortReqDTO> shortLinkGroupSortReqDTOList) {
        groupService.sortGroup(shortLinkGroupSortReqDTOList);
        return Results.success();
    }
}

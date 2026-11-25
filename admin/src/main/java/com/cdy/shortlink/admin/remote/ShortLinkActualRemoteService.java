package com.cdy.shortlink.admin.remote;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.dto.req.RecycleBinRecoverReqDTO;
import com.cdy.shortlink.admin.dto.req.RecycleBinRemoveReqDTO;
import com.cdy.shortlink.admin.dto.req.RecycleBinSaveReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkBatchCreateReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 短链接中台远程调用服务
 */
@FeignClient(value = "short-link-project", configuration = InternalApiFeignConfiguration.class)
public interface ShortLinkActualRemoteService {

    /**
     * 创建短链接
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接创建响应DTO
     */
    @PostMapping("/api/short-link/v1/create")
    Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO shortLinkCreateReqDTO);

    /**
     * 批量创建短链接
     *
     * @param requestParam 批量创建短链接请求参数
     * @return 短链接批量创建响应
     */
    @PostMapping("/api/short-link/v1/create/batch")
    Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam);

    /**
     * 修改短链接
     * @param shortLinkUpdateReqDTO 短链接修改请求DTO
     */
    @PostMapping("/api/short-link/v1/update")
    void updateShortLink(@RequestBody ShortLinkUpdateReqDTO shortLinkUpdateReqDTO);

    /**
     * 短链接分页
     * @param gid 分组标识
     * @param orderTag 排序字段
     * @param current 当前页码
     * @param size 每页数量
     * @return 短链接分页响应DTO
     */
    @GetMapping("/api/short-link/v1/page")
    Result<Page<ShortLinkPageRespDTO>> pageShortLink(
            @RequestParam("gid") String gid,
            @RequestParam("orderTag") String orderTag,
            @RequestParam("current") long current,
            @RequestParam("size") long size
    );

    /**
     * 短链接分组内数量
     * @param requestIds 短链接请求ID列表
     * @return 短链接分组内数量列表
     */
    @GetMapping("/api/short-link/v1/count")
    Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(@RequestParam("requestIds") List<String> requestIds);

    /**
     * 根据URL获取对应网站的标题
     * @param url 短链接URL
     * @return 短链接标题
     */
    @GetMapping("/api/short-link/v1/title")
    Result<String> getTitleByUrl(@RequestParam("url") String url);

    /**
     * 回收站保存
     * @param recycleBinSaveReqDTO 回收站保存请求DTO
     * @return 空
     */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    void saveRecycleBin(@RequestBody RecycleBinSaveReqDTO recycleBinSaveReqDTO);

    /**
     * 回收站分页
     * @param gidList 分组标识列表
     * @param current 当前页码
     * @param size 每页数量
     * @return 回收站分页响应DTO
     */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    Result<Page<ShortLinkPageRespDTO>> pageRecycleBin(
            @RequestParam("gidList") List<String> gidList,
            @RequestParam("current") long current,
            @RequestParam("size") long size
    );

    /**
     * 回收站恢复
     * @param recycleBinRecoverReqDTO 回收站恢复请求DTO
     * @return 空
     */
    @PostMapping("/api/short-link/v1/recycle-bin/recover")
    void recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO recycleBinRecoverReqDTO);

    /**
     * 回收站删除
     * @param recycleBinRemoveReqDTO 回收站删除请求DTO
     * @return 空
     */
    @PostMapping("/api/short-link/v1/recycle-bin/remove")
    void removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO recycleBinRemoveReqDTO);

    /**
     * 访问单个短链接指定时间内监控数据
     * @param fullShortUrl 実整短链接
     * @param gid 分组标识
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 短链接监控响应DTO
     */
    @GetMapping("/api/short-link/v1/stats")
    Result<ShortLinkStatsRespDTO> oneShortLinkStats(
            @RequestParam("fullShortUrl") String fullShortUrl,
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    /**
     * 访问分组短链接指定时间内监控数据
     * @param gid 分组标识
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 短链接分组监控响应DTO
     */
    @GetMapping("/api/short-link/v1/stats/group")
    Result<ShortLinkStatsRespDTO> groupShortLinkStats(
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     * @param fullShortUrl 実整短链接
     * @param gid 分组标识
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 短链接访问记录监控响应DTO
     */
    @GetMapping("/api/short-link/v1/stats/access-record")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            @RequestParam("fullShortUrl") String fullShortUrl,
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    /**
     * 访问分组短链接指定时间内访问记录监控数据
     * @param gid 分组标识
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 短链接分组访问记录监控响应DTO
     */
    @GetMapping("/api/short-link/v1/stats/access-record/group")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );
}

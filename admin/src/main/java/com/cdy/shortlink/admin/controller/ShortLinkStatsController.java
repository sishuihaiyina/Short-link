package com.cdy.shortlink.admin.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.common.convention.result.Results;
import com.cdy.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.cdy.shortlink.admin.dto.resp.ShortLinkOverviewRespDTO;
import com.cdy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkGroupStatsReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkStatsReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkDlqMessageRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 短链接监控控制层
 */
@Slf4j
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
                shortLinkStatsAccessRecordReqDTO.getEndDate(),
                shortLinkStatsAccessRecordReqDTO.getCurrent(),
                shortLinkStatsAccessRecordReqDTO.getSize()
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
                shortLinkGroupStatsAccessRecordReqDTO.getEndDate(),
                shortLinkGroupStatsAccessRecordReqDTO.getCurrent(),
                shortLinkGroupStatsAccessRecordReqDTO.getSize()
        );
    }

    /**
     * 导出访问记录 Excel
     * <p>
     * 按 500 条一页从 project 拉全量，最多拉 100 页（5 万条），避免一次导出把内存打爆。
     */
    @GetMapping("/api/short-link/admin/v1/stats/export")
    public void exportAccessRecord(HttpServletResponse response,
                                   @RequestParam("gid") String gid,
                                   @RequestParam(value = "fullShortUrl", required = false) String fullShortUrl,
                                   @RequestParam("startDate") String startDate,
                                   @RequestParam("endDate") String endDate,
                                   @RequestParam(value = "group", defaultValue = "false") boolean group) throws IOException {
        groupService.checkGidOwner(gid);
        List<ShortLinkStatsAccessRecordRespDTO> rows = new ArrayList<>();
        long current = 1;
        long size = 500;
        while (true) {
            Result<Page<ShortLinkStatsAccessRecordRespDTO>> result = group
                    ? shortLinkActualRemoteService.groupShortLinkStatsAccessRecord(gid, startDate, endDate, current, size)
                    : shortLinkActualRemoteService.shortLinkStatsAccessRecord(fullShortUrl, gid, startDate, endDate, current, size);
            Page<ShortLinkStatsAccessRecordRespDTO> page = result == null ? null : result.getData();
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }
            rows.addAll(page.getRecords());
            if (rows.size() >= page.getTotal() || current >= 100) {
                break;
            }
            current++;
        }
        List<AccessRecordExportRow> exportRows = new ArrayList<>(rows.size());
        for (ShortLinkStatsAccessRecordRespDTO row : rows) {
            exportRows.add(AccessRecordExportRow.of(row));
        }
        String fileName = URLEncoder.encode("访问记录_" + startDate + "_" + endDate + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        EasyExcel.write(response.getOutputStream(), AccessRecordExportRow.class)
                .sheet("访问记录")
                .doWrite(exportRows);
    }

    /**
     * 全站总览：当前用户所有分组的访问数据汇总
     * <p>
     * 分组数量有限（最多 20 个），逐个分组查项目侧统计再累加；单个分组查询失败不影响整体。
     */
    @GetMapping("/api/short-link/admin/v1/stats/overview")
    public Result<ShortLinkOverviewRespDTO> overview(@RequestParam("startDate") String startDate,
                                                     @RequestParam("endDate") String endDate) {
        List<ShortLinkGroupRespDTO> groups = groupService.listGroup();
        long totalLinks = 0L;
        long pv = 0L;
        long uv = 0L;
        long uip = 0L;
        int failedGroups = 0;
        for (ShortLinkGroupRespDTO group : groups) {
            if (group.getShortLinkCount() != null) {
                totalLinks += group.getShortLinkCount();
            }
            try {
                Result<ShortLinkStatsRespDTO> result =
                        shortLinkActualRemoteService.groupShortLinkStats(group.getGid(), startDate, endDate);
                if (result != null && result.getData() != null) {
                    pv += nullToZero(result.getData().getPv());
                    uv += nullToZero(result.getData().getUv());
                    uip += nullToZero(result.getData().getUip());
                }
            } catch (Exception ex) {
                failedGroups++;
                log.warn("全站总览：分组统计查询失败，gid：{}", group.getGid(), ex);
            }
        }
        return Results.success(ShortLinkOverviewRespDTO.builder()
                .totalLinks(totalLinks)
                .groupCount(groups.size())
                .pv(pv)
                .uv(uv)
                .uip(uip)
                .failedGroups(failedGroups)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }

    /**
     * 统计消息死信分页
     */
    @GetMapping("/api/short-link/admin/v1/mq/dlq")
    public Result<Page<ShortLinkDlqMessageRespDTO>> pageDlq(@RequestParam(value = "current", defaultValue = "1") long current,
                                                            @RequestParam(value = "size", defaultValue = "10") long size) {
        return shortLinkActualRemoteService.pageDlq(current, size);
    }

    /**
     * 重放一条死信消息
     */
    @PostMapping("/api/short-link/admin/v1/mq/dlq/replay")
    public Result<Void> replayDlq(@RequestParam("id") Long id) {
        return shortLinkActualRemoteService.replayDlq(id);
    }

    private static long nullToZero(Integer value) {
        return value == null ? 0L : value;
    }

    /**
     * 导出用的行模型
     */
    @Data
    @NoArgsConstructor
    public static class AccessRecordExportRow {

        @ExcelProperty("访问时间")
        @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
        private Date createTime;

        @ExcelProperty("访问IP")
        private String ip;

        @ExcelProperty("访客地区")
        private String locale;

        @ExcelProperty("浏览器")
        private String browser;

        @ExcelProperty("操作系统")
        private String os;

        @ExcelProperty("设备")
        private String device;

        @ExcelProperty("访问网络")
        private String network;

        @ExcelProperty("访客类型")
        private String uvType;

        static AccessRecordExportRow of(ShortLinkStatsAccessRecordRespDTO source) {
            AccessRecordExportRow row = new AccessRecordExportRow();
            if (source == null) {
                return row;
            }
            row.setCreateTime(source.getCreateTime());
            row.setIp(source.getIp());
            row.setLocale(source.getLocale());
            row.setBrowser(source.getBrowser());
            row.setOs(source.getOs());
            row.setDevice(source.getDevice());
            row.setNetwork(source.getNetwork());
            row.setUvType(source.getUvType());
            return row;
        }
    }
}

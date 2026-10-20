package com.cdy.shortlink.admin.remote;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.dto.req.RecycleBinRecoverReqDTO;
import com.cdy.shortlink.admin.dto.req.RecycleBinRemoveReqDTO;
import com.cdy.shortlink.admin.dto.req.RecycleBinSaveReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.*;
import com.cdy.shortlink.admin.remote.dto.resp.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短链接远程调用服务
 */
public interface ShortLinkRemoteService {

    /**
     * 创建短链接
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接创建响应DTO
     */
    default Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO shortLinkCreateReqDTO) {
        String resultBodyStr = HttpUtil.post("http://localhost:8001/api/short-link/v1/create", JSON.toJSONString(shortLinkCreateReqDTO));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {});
    }

    /**
     * 短链接分页
     * @param shortLinkPageReqDTO 短链接分页请求DTO
     * @return 短链接分页响应DTO
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO shortLinkPageReqDTO) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("gid", shortLinkPageReqDTO.getGid());
        requestMap.put("orderTag", shortLinkPageReqDTO.getOrderTag());
        requestMap.put("current", shortLinkPageReqDTO.getCurrent());
        requestMap.put("size", shortLinkPageReqDTO.getSize());
        String resultPageStr = HttpUtil.get("http://localhost:8001/api/short-link/v1/page", requestMap);

        return JSON.parseObject(resultPageStr, new TypeReference<>() {});
    }

    /**
     * 短链接分组内数量
     * @param requestIds 短链接请求ID列表
     * @return 短链接分组内数量列表
     */
    default Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestIds) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("requestIds", requestIds);
        String resultPageStr = HttpUtil.get("http://localhost:8001/api/short-link/v1/count", requestMap);

        return JSON.parseObject(resultPageStr, new TypeReference<>() {});
    }

    /**
     * 修改短链接
     * @param shortLinkUpdateReqDTO 短链接修改请求DTO
     */
    default void updateShortLink(ShortLinkUpdateReqDTO shortLinkUpdateReqDTO) {
        HttpUtil.post("http://localhost:8001/api/short-link/v1/update", JSON.toJSONString(shortLinkUpdateReqDTO));
    }

    /**
     * 根据URL获取对应网站的标题
     * @param url 短链接URL
     * @return 短链接标题
     */
    default Result<String> getTitleByUrl(@RequestParam("url") String url) {
        String resultStr = HttpUtil.get("http://localhost:8001/api/short-link/v1/title?url=" + url);
        return JSON.parseObject(resultStr, new TypeReference<>() {});
    }

    /**
     * 回收站保存
     * @param recycleBinSaveReqDTO 回收站保存请求DTO
     * @return 空
     */
    default void saveRecycleBin(RecycleBinSaveReqDTO recycleBinSaveReqDTO) {
        HttpUtil.post("http://localhost:8001/api/short-link/v1/recycle-bin/save", JSON.toJSONString(recycleBinSaveReqDTO));
    }

    /**
     * 回收站分页
     * @param recycleBinPageReqDTO 回收站分页请求DTO
     * @return 回收站分页响应DTO
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO recycleBinPageReqDTO) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("gidList", recycleBinPageReqDTO.getGidList());
        requestMap.put("current", recycleBinPageReqDTO.getCurrent());
        requestMap.put("size", recycleBinPageReqDTO.getSize());
        String resultPageStr = HttpUtil.get("http://localhost:8001/api/short-link/v1/recycle-bin/page", requestMap);

        return JSON.parseObject(resultPageStr, new TypeReference<>() {});
    }

    /**
     * 回收站恢复
     * @param recycleBinRecoverReqDTO 回收站恢复请求DTO
     * @return 空
     */
    default void recoverRecycleBin(RecycleBinRecoverReqDTO recycleBinRecoverReqDTO) {
        HttpUtil.post("http://localhost:8001/api/short-link/v1/recycle-bin/recover", JSON.toJSONString(recycleBinRecoverReqDTO));
    }

    /**
     * 回收站删除
     * @param recycleBinRemoveReqDTO 回收站删除请求DTO
     * @return 空
     */
    default void removeRecycleBin(RecycleBinRemoveReqDTO recycleBinRemoveReqDTO) {
        HttpUtil.post("http://localhost:8001/api/short-link/v1/recycle-bin/remove", JSON.toJSONString(recycleBinRemoveReqDTO));
    }

    /**
     * 访问单个短链接指定时间内监控数据
     *
     * @param requestParam 访问短链接监控请求参数
     * @return 短链接监控信息
     */
    default Result<ShortLinkStatsRespDTO> oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        String resultBodyStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats", BeanUtil.beanToMap(requestParam));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     *
     * @param shortLinkStatsAccessRecordReqDTO 访问短链接监控记录请求参数
     * @return 短链接访问记录监控信息
     */
    default Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO shortLinkStatsAccessRecordReqDTO) {
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(shortLinkStatsAccessRecordReqDTO, false, true);
        stringObjectMap.remove("orders");
        stringObjectMap.remove("records");
        String resultBodyStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats/access-record", stringObjectMap);
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }
}

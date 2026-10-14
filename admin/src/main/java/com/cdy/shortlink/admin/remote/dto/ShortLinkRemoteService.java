package com.cdy.shortlink.admin.remote.dto;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.cdy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

import java.util.HashMap;
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
        requestMap.put("current", shortLinkPageReqDTO.getCurrent());
        requestMap.put("size", shortLinkPageReqDTO.getSize());
        String resultPageStr = HttpUtil.get("http://localhost:8001/api/short-link/v1/page", requestMap);

        return JSON.parseObject(resultPageStr, new TypeReference<>() {});
    }


}

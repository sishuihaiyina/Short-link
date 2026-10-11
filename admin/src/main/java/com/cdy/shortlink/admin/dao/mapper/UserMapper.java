package com.cdy.shortlink.admin.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdy.shortlink.admin.dao.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户持久层接口
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}

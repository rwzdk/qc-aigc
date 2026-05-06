package com.qc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qc.system.entity.ApiAccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * API 访问日志 Mapper
 */
@Mapper
public interface ApiAccessLogMapper extends BaseMapper<ApiAccessLog> {
}

package com.mars.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.message.entity.SysNoticeSendLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知推送记录 Mapper
 */
@Mapper
public interface SysNoticeSendLogMapper extends BaseMapper<SysNoticeSendLog> {
}

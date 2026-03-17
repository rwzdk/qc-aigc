package com.mars.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.file.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {
}

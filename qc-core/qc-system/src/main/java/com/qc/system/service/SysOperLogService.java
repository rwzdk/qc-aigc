package com.qc.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qc.common.result.PageResult;
import com.qc.system.entity.SysOperLog;

/**
 * 操作日志服务接口
 */
public interface SysOperLogService extends IService<SysOperLog> {

    /**
     * 分页查询操作日志
     */
    PageResult<SysOperLog> page(Integer page, Integer pageSize, String title, String operName, Integer status);

    /**
     * 记录操作日志
     */
    void recordLog(SysOperLog operLog);

    /**
     * 清空操作日志
     */
    void clean();
}

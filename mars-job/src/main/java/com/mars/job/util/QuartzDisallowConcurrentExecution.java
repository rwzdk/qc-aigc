package com.mars.job.util;

import com.mars.job.entity.SysJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

/**
 * 定时任务执行（禁止并发执行）
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution extends AbstractQuartzJob {
    
    @Override
    protected void doExecute(JobExecutionContext context, SysJob job) throws Exception {
        JobInvokeUtil.invokeMethod(job);
    }
}

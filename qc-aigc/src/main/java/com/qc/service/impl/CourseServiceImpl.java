package com.qc.service.impl;

import com.qc.entity.po.Course;
import com.qc.mapper.CourseMapper;
import com.qc.service.ICourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 学科表 服务实现类
 * </p>
 *
 * @author qc
 * @since 2025-07-15
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {
}

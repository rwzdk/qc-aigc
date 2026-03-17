package com.mars.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.system.entity.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生表 Mapper
 * 
 * @author Mars
 * @date 2026-03-01
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {

}

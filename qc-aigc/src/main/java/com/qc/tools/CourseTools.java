package com.qc.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.qc.entity.po.Course;
import com.qc.entity.po.CourseReservation;
import com.qc.entity.po.School;
import com.qc.entity.query.CourseQuery;
import com.qc.service.ICourseReservationService;
import com.qc.service.ICourseService;
import com.qc.service.ISchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CourseTools {
    private final ICourseService  courseService;
    private final ISchoolService schoolService;
    private final ICourseReservationService reservationService;

      @Tool(description = "根据条件查询课程")
      public List<Course> queryCourse(@ToolParam(description = "查询的条件",required = false) CourseQuery query) {
          //判断查询条件是否为空
          if(query==null){
              //如果为空，则返回一个空的List
              return List.of();
          }
          LambdaQueryWrapper<Object> lambdaQueryWrapper = new LambdaQueryWrapper<>();
          //查询 1.type 2.edu<=2
          //使用QueryChainWrapper来构建查询条件
          //调用courseservice的query方法因为这里需要调用courseMapper的selectList方法来查询数据库
          QueryChainWrapper<Course> wrapper = courseService.query()
                  //如果查询条件中的type不为空，则添加type查询条件
                  .eq(query.getType() != null, "type", query.getType())
                  //如果查询条件中的edu不为空，则添加edu查询条件
                  .le(query.getEdu() != null, "edu", query.getEdu());
          //如果查询条件中的sorts不为空，则添加排序条件
          if(query.getSorts()!=null &&!query.getSorts().isEmpty()){
              //遍历sorts，添加排序条件
              for (CourseQuery.Sort sort : query.getSorts()) {
                  wrapper.orderBy(true,sort.getAsc(),sort.getField());
              }
          }
          //将前面设定好的查询条件生成对应的SQL查询语句,随后执行这条SQL语句将查询到course对象封装到一个List<Course>集合中并返回
          return wrapper.list();
      }

      @Tool(description = "查询所有校区")
      public List<School> queryAllSchool(){
          return schoolService.list();
      }

/**
 * 生成课程预约单,并返回生成的预约单号
 * @param course 预约课程
 * @param school 预约校区
 * @param studentName 学生姓名
 * @param contactInfo 联系电话
 * @param remark 备注(非必填)
 * @return 返回生成的预约单号
 */
      @Tool(description = "生成课程预约单,并返回生成的预约单号")
      public String generateCourseReservation(@ToolParam(description = "预约课程") String course,
                                              @ToolParam(description = "预约校区") String school,
                                              @ToolParam(description = "学生姓名") String studentName,
                                              @ToolParam(description = "联系电话") String contactInfo,
                                              @ToolParam(description = "备注",required = false) String remark
                                                                 ){
          //创建课程预约单对象
          CourseReservation courseReservation = new CourseReservation();
          //设置预约课程
          courseReservation.setCourse(course);
          //设置学生姓名
          courseReservation.setStudentName(studentName);
          //设置联系电话
          courseReservation.setContactInfo(contactInfo);
          //设置预约校区
          courseReservation.setSchool(school);
          //设置备注
          courseReservation.setRemark(remark);

          //将生成课程预约单进行保存
          reservationService.save(courseReservation);

          //返回生成的预约单号
          return String.valueOf(courseReservation.getId());
      }

}

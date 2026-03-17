package com.qc.entity.query;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

@Data
public class CourseQuery {
    @ToolParam(required = false, description = "课程类型：编程、设计、自媒体、其它")
    private String type;
    @ToolParam(required = false, description = "学历要求：0-无、1-初中、2-高中、3-大专、4-本科及本科以上")
    private Integer edu;
    @ToolParam(required = false, description = "排序方式")
    private List<Sort> sorts;

    //静态内部类
    //存储排序方式的字段
    //@Data注解也会为这个静态内部类生成getter和setter方法
    @Data
    public static class Sort {
        //排序字段: price或duration
        @ToolParam(required = false, description = "排序字段: price或duration")
        private String field;
        //是否是升序: true/false
        @ToolParam(required = false, description = "是否是升序: true/false")
        private Boolean asc;
    }
    //
}
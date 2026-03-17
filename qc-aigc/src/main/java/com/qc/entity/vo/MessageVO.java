package com.qc.entity.vo;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;


@Data
public class MessageVO {
    private String role;
    private String content;

    public MessageVO(Message message) {
        //加上break避免判定成功后面的代码执行,覆盖正确赋值
        switch (message.getMessageType()){
                case USER :
                   role="user";
                   break;
                case ASSISTANT:
                   role="assistant";
                   break;
                default:
                   role="";
                   break;
        }
        this.content=message.getText();
    }
}

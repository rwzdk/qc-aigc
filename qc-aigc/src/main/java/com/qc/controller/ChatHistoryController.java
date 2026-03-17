package com.qc.controller;

import com.qc.entity.vo.MessageVO;
import com.qc.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/history")
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatMemory chatMemory;

    @GetMapping("/{type}")
    // 根据type参数获取聊天记录的id列表
    public List<String> getChatIds(@PathVariable("type")String type){
        // 调用chatHistoryRepository的getChatIds方法，传入type参数，返回聊天记录的id列表
        return chatHistoryRepository.getChatIds(type);
    }


    @GetMapping("/{type}/{chatId}")
    //根据type和chatId获取聊天记录
    public List<MessageVO> getChatHistory(@PathVariable("type")String type,@PathVariable("chatId")String chatId){
        //从chatMemory中获取聊天记录
        List<Message> messages = chatMemory.get(chatId, Integer.MAX_VALUE);
        //如果获取不到聊天记录，则返回空列表
        if(messages == null){
            return List.of();
        }
        //将messages转换成messageVO进行返回,调用messagevo的有参构造,将message作为参数进行构造并且以list的形式进行收集
        return messages.stream().map(MessageVO::new).toList();
    }
}

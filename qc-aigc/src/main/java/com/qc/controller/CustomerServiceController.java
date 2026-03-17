package com.qc.controller;

import com.qc.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class CustomerServiceController {
    private final ChatClient serviceChatClient;
    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping(value="/service",produces = "text/html;charset=utf-8")
    public Flux<String> service(String prompt,String chatId){
        chatHistoryRepository.save("service",chatId);
        return serviceChatClient.prompt()
                .user(prompt)
                .advisors(a->a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,chatId))
                .stream()
                .content();
    }

}

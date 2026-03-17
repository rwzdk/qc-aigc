package com.qc.repository;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
//(默认是单例,外界操作的是同一实例的map集合)
public class InMemoryChatHistoryRepository implements ChatHistoryRepository{

    private final Map<String,List<String>> chatHistory=new HashMap<>();

    @Override
    // 重写save方法，传入type和chatId参数
    public void save(String type, String chatId) {
        // 获取chatHistory中type对应的List，如果不存在则创建一个新的ArrayList
        List<String> chatIds = chatHistory.computeIfAbsent(type, k -> new ArrayList<>());
        // 如果chatIds中已经包含chatId，则直接返回
        if(chatIds.contains(chatId)){
            return;
        }
        // 否则将chatId添加到chatIds中
        chatIds.add(chatId);
    }

    @Override
    public List<String> getChatIds(String type) {
       return chatHistory.getOrDefault(type, List.of());//根据type去取值,取到了就返回,没取到就返回空集合
    }
}

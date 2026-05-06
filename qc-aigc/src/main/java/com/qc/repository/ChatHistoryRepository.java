package com.qc.repository;

import java.util.List;

public interface ChatHistoryRepository {
    /*
    * 保存会话记录
    * @param type业务类型,如chat,service,pdf
    * @param chatId 会话ID
    * */
    void save(String type,String chatId);
    /*
    *获取会话列表
    * @param type业务类型,如chat,service,pdf
    * */
    List<String>getChatIds(String type);
}

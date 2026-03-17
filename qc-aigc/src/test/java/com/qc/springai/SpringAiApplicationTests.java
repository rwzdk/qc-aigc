package com.qc.springai;

import com.qc.util.VectorDistanceUtils;
import org.apache.tomcat.util.net.openssl.OpenSSLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
class SpringAiApplicationTests {
    @Autowired
    private OpenAiEmbeddingModel embeddingModel;
    @Autowired
    private VectorStore vectorStore;
  @Test
  public void testEmbedding() {
      // 1.测试数据
      // 1.1.用来查询的文本，国际冲突
      String query = "global conflicts";

      // 1.2.用来做比较的文本
      String[] texts = new String[]{
              "哈马斯称加沙下阶段停火谈判仍在进行 以方尚未做出承诺",
              "土耳其、芬兰、瑞典与北约代表将继续就瑞典“入约”问题进行谈判",
              "日本航空基地水井中检测出有机氟化物超标",
              "国家游泳中心（水立方）：恢复游泳、嬉水乐园等水上项目运营",
              "我国首次在空间站开展舱外辐射生物学暴露实验",
      };
      // 2.向量化
      // 2.1.先将查询文本向量化
      float[] queryVector = embeddingModel.embed(query);

      // 2.2.再将比较文本向量化，放到一个数组
      List<float[]> textVectors = embeddingModel.embed(Arrays.asList(texts));

      // 3.比较欧氏距离
      // 3.1.把查询文本自己与自己比较，肯定是相似度最高的
      System.out.println(VectorDistanceUtils.euclideanDistance(queryVector, queryVector));
      // 3.2.把查询文本与其它文本比较
      for (float[] textVector : textVectors) {
          System.out.println(VectorDistanceUtils.euclideanDistance(queryVector, textVector));
      }
      System.out.println("------------------");

      // 4.比较余弦距离
      // 4.1.把查询文本自己与自己比较，肯定是相似度最高的
      System.out.println(VectorDistanceUtils.cosineDistance(queryVector, queryVector));
      // 4.2.把查询文本与其它文本比较
      for (float[] textVector : textVectors) {
          System.out.println(VectorDistanceUtils.cosineDistance(queryVector, textVector));
      }
  }
  @Test
    public void testVectorStore(){
      //1.设置要解读的pdf资源
      Resource resource = new FileSystemResource("中二知识笔记.pdf");

      //2.创建PDF的读取器每页去读取pdf的内容转换成document的形式
      PagePdfDocumentReader reader = new PagePdfDocumentReader(
              resource, // 文件源
              PdfDocumentReaderConfig.builder()
                      .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                      .withPagesPerDocument(1) // 每1页PDF作为一个Document
                      .build()
      );

      //3.读取PDF文档,拆分为Documents
      List<Document> documents = reader.read();

      //4.将转换完的document写入向量库
      //通过配置好的向量化模型将存入的数据向量化
      vectorStore.add(documents);

      //5.设置搜索的要求
      SearchRequest request=new SearchRequest().builder()
              .query("\"论语的教育目的是什么\"")//查询的内容
              .topK(2)//前几个相似度高的内容
              .similarityThreshold(0.5)//相似度的最低要求
              .filterExpression("file_name == '中二知识笔记.pdf'")//指定进行搜索的文件
              .build();

      //6.在向量数据库中利用向量模型进行检索内容
      List<Document> docs = vectorStore.similaritySearch(request);
      if(docs==null){
          System.out.println("没有找到任何内容");
          return;
      }
      for (Document doc : docs) {
          System.out.println(doc.getId());
          System.out.println(doc.getScore());//相似度
          System.out.println(doc.getText());
      }

  }
}

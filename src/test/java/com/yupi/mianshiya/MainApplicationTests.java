package com.yupi.mianshiya;

import com.alibaba.fastjson.JSON;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主类测试
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@SpringBootTest
class MainApplicationTests {

    @Test
    void contextLoads() {
    }

    @Resource
    private QuestionService questionService;

    /**
     * 批量生成10000个测试题目
     */
    @Test
    @Transactional
    @Rollback(false) // 不回滚，保留测试数据
    public void generateTestQuestions() {
        System.out.println("开始生成10000个测试题目...");

        List<Question> questionList = new ArrayList<>();
        Date now = new Date();

        // 生成10000个题目
        for (int i = 12345; i <= 22345; i++) {
            Question question = createTestQuestion(i, now);
            questionList.add(question);

            // 每1000条批量插入一次
            if (i % 1000 == 0) {
                boolean success = questionService.saveBatch(questionList);
                if (success) {
                    System.out.println("已生成 " + i + " 个题目");
                } else {
                    System.err.println("批量插入失败，当前批次：" + (i - 999) + " - " + i);
                }
                questionList.clear();
            }
        }

        // 处理剩余的题目
        if (!questionList.isEmpty()) {
            questionService.saveBatch(questionList);
        }

        System.out.println("测试题目生成完成！");
    }

    /**
     * 创建单个测试题目
     */
    private Question createTestQuestion(int index, Date createTime) {
        Question question = new Question();

        // 设置标题
        question.setTitle("测试题目 " + index + " - " + getQuestionType(index));

        // 设置内容
        question.setContent(generateQuestionContent(index));

        // 设置标签（JSON数组格式）
        question.setTags(generateTags(index));

        // 设置答案
        question.setAnswer(generateAnswer(index));

        // 设置用户ID（使用测试用户ID 1）
        question.setUserId(1L);

        // 设置时间
        question.setCreateTime(createTime);
        question.setUpdateTime(createTime);
        question.setEditTime(createTime);

        // 设置未删除
        question.setIsDelete(0);

        return question;
    }

    /**
     * 根据索引生成题目类型
     */
    private String getQuestionType(int index) {
        String[] types = {"算法", "数据结构", "Java", "Spring", "MySQL", "Redis", "设计模式", "网络", "操作系统", "前端"};
        return types[index % types.length];
    }

    /**
     * 生成题目内容
     */
    private String generateQuestionContent(int index) {
        String[] templates = {
                "请解释什么是" + getQuestionType(index) + "？题目编号：" + index,
                "如何在项目中使用" + getQuestionType(index) + "？请详细说明。题目编号：" + index,
                "" + getQuestionType(index) + "的优缺点是什么？请举例说明。题目编号：" + index,
                "请比较" + getQuestionType(index) + "与其他技术的区别。题目编号：" + index,
                "在什么场景下应该使用" + getQuestionType(index) + "？题目编号：" + index
        };
        return templates[index % templates.length];
    }

    /**
     * 生成标签（JSON数组格式）
     */
    private String generateTags(int index) {
        String[][] tagGroups = {
                {"算法", "编程", "面试"},
                {"数据结构", "计算机基础", "算法"},
                {"Java", "后端", "编程语言"},
                {"Spring", "框架", "Java"},
                {"MySQL", "数据库", "SQL"},
                {"Redis", "缓存", "NoSQL"},
                {"设计模式", "软件工程", "架构"},
                {"网络", "计算机网络", "协议"},
                {"操作系统", "计算机基础", "系统"},
                {"前端", "JavaScript", "Web"}
        };

        String[] tags = tagGroups[index % tagGroups.length];
        List<String> tagList = List.of(tags);
        return JSON.toJSONString(tagList);
    }

    /**
     * 生成答案
     */
    private String generateAnswer(int index) {
        return "这是题目 " + index + " 的标准答案。" + getQuestionType(index) + "是一个重要的技术概念，" +
                "在实际开发中有着广泛的应用。具体来说：\n" +
                "1. 定义和特点\n" +
                "2. 使用场景\n" +
                "3. 实现方式\n" +
                "4. 注意事项\n" +
                "更多详细内容请参考相关技术文档。";
    }

    /**
     * 获取所有题目ID列表（用于JMeter测试）
     */
    @Test
    public void getQuestionIdsForJMeter() {
        System.out.println("正在获取所有题目ID...");

        // 查询所有题目ID
        List<Question> questionList = questionService.list();
        List<Long> questionIds = questionList.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        System.out.println("总共找到 " + questionIds.size() + " 个题目");

        // 输出JSON格式（用于JMeter参数化）
        System.out.println("\n=== JSON格式（用于JMeter） ===");
        System.out.println(JSON.toJSONString(questionIds));

        // 输出前100个ID作为示例
        if (questionIds.size() > 100) {
            List<Long> sampleIds = questionIds.subList(0, 100);
            System.out.println("\n=== 前100个ID示例 ===");
            System.out.println(JSON.toJSONString(sampleIds));
        }

        // 生成CSV文件
        generateCsvFile(questionIds);
    }

    /**
     * 生成CSV文件供JMeter使用
     */
    private void generateCsvFile(List<Long> questionIds) {
        try {
            String fileName = "question_ids.csv";
            FileWriter writer = new FileWriter(fileName);

            // 写入CSV头
            writer.write("questionId\n");

            // 写入所有题目ID
            for (Long questionId : questionIds) {
                writer.write(questionId + "\n");
            }

            writer.close();
            System.out.println("\nCSV文件已生成：" + fileName);
            System.out.println("可以在JMeter中使用CSV Data Set Config读取此文件");

        } catch (IOException e) {
            System.err.println("生成CSV文件失败：" + e.getMessage());
        }
    }

    /**
     * 清理测试数据
     */
    @Test
    @Transactional
    @Rollback(false)
    public void cleanTestData() {
        System.out.println("开始清理测试数据...");

        // 删除标题包含"测试题目"的数据
        questionService.lambdaUpdate()
                .like(Question::getTitle, "测试题目")
                .remove();

        System.out.println("测试数据清理完成！");
    }

    /**
     * 获取指定数量的题目ID（用于小规模测试）
     */
    @Test
    public void getLimitedQuestionIds() {
        int limit = 1000; // 获取1000个ID用于测试

        List<Question> questionList = questionService.lambdaQuery()
                .select(Question::getId)
                .last("LIMIT " + limit)
                .list();

        List<Long> questionIds = questionList.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        System.out.println("获取到 " + questionIds.size() + " 个题目ID：");
        System.out.println(JSON.toJSONString(questionIds));
    }

}

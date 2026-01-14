package com.yupi.mianshiya.rabbitmq;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionBankAddMessage {

    private Long questionId;

    private Long questionBankId;

    private Long userId;


    // private LocalDateTime createTime = LocalDateTime.now();
}




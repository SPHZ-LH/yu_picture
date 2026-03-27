package com.yupi.yupicturebackend.api.aliyunai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImageOutPaintingResponse {
    private Output output;
    private String code;
    private String message;

    @Data
    @NoArgsConstructor
    public static class Output {
        @JsonProperty("task_id")
        private String taskId;

        @JsonProperty("task_status")
        private TaskStatus taskStatus;

        @JsonProperty("request_id")
        private String requestId;
    }

    public enum TaskStatus {
        /**
         * 任务排队中
         */
        PENDING,
        /**
         * 任务处理中
         */
        RUNNING,
        /**
         * 任务执行成功
         */
        SUCCEEDED,
        /**
         * 任务执行失败
         */
        FAILED,
        /**
         * 任务已取消
         */
        CANCELED,
        /**
         * 任务不存在或状态未知
         */
        UNKNOWN
    }
}

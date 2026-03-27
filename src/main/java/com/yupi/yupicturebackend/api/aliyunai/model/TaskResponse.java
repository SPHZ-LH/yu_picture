package com.yupi.yupicturebackend.api.aliyunai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 任务响应实体类
 */
@Data
public class TaskResponse {

    @JsonProperty("request_id")
    private String requestId;        // 请求唯一标识

    private Output output;           // 输出结果对象

    // ==================== 便捷方法 ====================

    /**
     * 获取任务ID
     */
    public String getTaskId() {
        return output != null ? output.getTaskId() : null;
    }

    /**
     * 获取任务状态
     */
    public TaskStatus getTaskStatus() {
        return output != null ? output.getTaskStatus() : null;
    }

    /**
     * 获取输出图片URL
     */
    public String getOutputImageUrl() {
        if (output == null) {
            return null;
        }
        
        // 优先从outputImageUrl字段获取（图像扩展任务使用此字段）
        if (output.getOutputImageUrl() != null) {
            return output.getOutputImageUrl();
        }
        
        // 其次从results数组获取（其他任务类型可能使用此字段）
        if (output.getResults() != null && !output.getResults().isEmpty()) {
            Result firstResult = output.getResults().get(0);
            if (firstResult != null && firstResult.getUrl() != null) {
                return firstResult.getUrl();
            }
        }
        
        return null;
    }

    /**
     * 获取错误码
     */
    public String getCode() {
        return output != null ? output.getCode() : null;
    }

    /**
     * 获取错误信息
     */
    public String getMessage() {
        return output != null ? output.getMessage() : null;
    }

    // ==================== 内部类定义 ====================

    /**
     * 输出结果对象
     */
    @Data
    public static class Output {
        @JsonProperty("task_id")
        private String taskId;           // 任务ID

        @JsonProperty("task_status")
        private TaskStatus taskStatus;   // 任务状态

        @JsonProperty("submit_time")
        private String submitTime;       // 任务提交时间

        @JsonProperty("scheduled_time")
        private String scheduledTime;    // 任务调度时间

        @JsonProperty("end_time")
        private String endTime;          // 任务完成时间

        private String code;             // 错误码（仅失败时返回）
        private String message;          // 错误信息（仅失败时返回）

        @JsonProperty("output_image_url")
        private String outputImageUrl;   // 输出图片URL（直接在output层级）

        @JsonProperty("task_metrics")
        private TaskMetrics taskMetrics; // 任务结果统计

        private java.util.List<Result> results; // 结果列表（某些场景可能使用）

        private Usage usage;             // 图像统计信息
    }

    /**
     * 单个结果对象
     */
    @Data
    public static class Result {
        private String url;              // 图片URL
    }

    // ==================== 内部类定义 ====================

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,    // 任务排队中
        RUNNING,    // 任务处理中
        SUCCEEDED,  // 任务执行成功
        FAILED,     // 任务执行失败
        CANCELED,   // 任务已取消
        UNKNOWN     // 任务不存在或状态未知
    }

    /**
     * 任务结果统计
     */
    @Data
    public static class TaskMetrics {
        private Integer TOTAL;     // 总任务数
        private Integer SUCCEEDED; // 成功任务数
        private Integer FAILED;    // 失败任务数
    }

    /**
     * 图像统计信息
     */
    @Data
    public static class Usage {
        @JsonProperty("image_count")
        private Integer imageCount; // 成功生成的图片数量
    }
}

package com.yupi.yupicturebackend.api.aliyunai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yupicturebackend.api.aliyunai.model.ImageOutPaintingRequest;
import com.yupi.yupicturebackend.api.aliyunai.model.ImageOutPaintingResponse;
import com.yupi.yupicturebackend.api.aliyunai.model.TaskResponse;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 阿里云AI API调用类
 *
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-27
 * @description 封装阿里云通义万相图像扩展API调用
 */
@Slf4j
@Component
public class AliYunAiApi {

    /**
     * 阿里云AI API Key
     */
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    /**
     * 创建图像扩展任务的API端点
     */
    private static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    /**
     * 查询任务结果的API端点模板
     */
    private static final String GET_TASK_URL_TEMPLATE = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    /**
     * HTTP请求超时时间（毫秒）
     */
    private static final int TIMEOUT = 60000;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 步骤1：创建图像扩展任务，获取任务ID
     *
     * @param request 图像扩展请求参数
     * @return 任务响应结果，包含任务ID
     * @throws BusinessException 当API调用失败时抛出业务异常
     */
    public ImageOutPaintingResponse createOutPaintingTask(ImageOutPaintingRequest request) {
        // 参数校验
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }

        // 构建请求体 - 使用Jackson来正确处理@JsonProperty注解
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(request);
            log.info("创建图像扩展任务，请求参数: {}", requestBody);
        } catch (Exception e) {
            log.error("请求参数序列化失败", e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数序列化失败");
        }

        // 发送HTTP POST请求
        try {
            HttpResponse response = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-DashScope-Async", "enable")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .timeout(TIMEOUT)
                    .execute();

            // 获取响应内容
            String responseBody = response.body();
            log.info("创建图像扩展任务响应: {}", responseBody);

            // 检查HTTP状态码
            if (!response.isOk()) {
                log.error("创建任务失败，HTTP状态码: {}, 响应内容: {}", response.getStatus(), responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建图像扩展任务失败");
            }

            // 解析响应JSON - 使用Jackson解析
            ImageOutPaintingResponse outPaintingResponse = objectMapper.readValue(responseBody, ImageOutPaintingResponse.class);

            // 校验响应结果
            if (outPaintingResponse == null || outPaintingResponse.getOutput() == null) {
                log.error("响应数据解析失败: {}", responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "响应数据解析失败");
            }

            // 检查业务错误码
            if (outPaintingResponse.getCode() != null) {
                log.error("创建任务业务异常，错误码: {}, 错误信息: {}", outPaintingResponse.getCode(), outPaintingResponse.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建任务失败: " + outPaintingResponse.getMessage());
            }

            return outPaintingResponse;

        } catch (Exception e) {
            log.error("创建图像扩展任务异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建图像扩展任务异常: " + e.getMessage());
        }
    }

    /**
     * 步骤2：根据任务ID查询任务执行结果
     *
     * @param taskId 任务ID
     * @return 任务详细信息，包含任务状态和结果图片URL
     * @throws BusinessException 当API调用失败时抛出业务异常
     */
    public TaskResponse getTaskResult(String taskId) {
        // 参数校验
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        }

        // 构建查询URL
        String queryUrl = String.format(GET_TASK_URL_TEMPLATE, taskId);
        log.info("查询任务结果，任务ID: {}, URL: {}", taskId, queryUrl);

        // 发送HTTP GET请求
        try {
            HttpResponse response = HttpRequest.get(queryUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(TIMEOUT)
                    .execute();

            // 获取响应内容
            String responseBody = response.body();
            log.info("查询任务结果响应: {}", responseBody);

            // 检查HTTP状态码
            if (!response.isOk()) {
                log.error("查询任务失败，HTTP状态码: {}, 响应内容: {}", response.getStatus(), responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "查询任务结果失败");
            }

            // 解析响应JSON - 使用Jackson解析
            TaskResponse taskResponse = objectMapper.readValue(responseBody, TaskResponse.class);

            // 校验响应结果
            if (taskResponse == null) {
                log.error("响应数据解析失败: {}", responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "响应数据解析失败");
            }

            // 检查业务错误码
            if (taskResponse.getCode() != null) {
                log.error("查询任务业务异常，错误码: {}, 错误信息: {}",
                        taskResponse.getCode(), taskResponse.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "查询任务失败: " + taskResponse.getMessage());
            }

            return taskResponse;

        } catch (Exception e) {
            log.error("查询任务结果异常，任务ID: {}", taskId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "查询任务结果异常: " + e.getMessage());
        }
    }

    /**
     * 轮询查询任务结果，直到任务完成或失败
     *
     * @param taskId        任务ID
     * @param maxRetries    最大重试次数
     * @param retryInterval 重试间隔（毫秒）
     * @return 任务最终结果
     * @throws BusinessException 当任务失败或超时时抛出业务异常
     */
    public TaskResponse pollTaskResult(String taskId, int maxRetries, long retryInterval) {
        int retryCount = 0;

        while (retryCount < maxRetries) {
            TaskResponse taskResponse = getTaskResult(taskId);
            TaskResponse.TaskStatus status = taskResponse.getTaskStatus();

            log.info("任务状态查询，任务ID: {}, 状态: {}, 重试次数: {}/{}",
                    taskId, status, retryCount + 1, maxRetries);

            // 任务成功完成
            if (TaskResponse.TaskStatus.SUCCEEDED.equals(status)) {
                log.info("任务执行成功，任务ID: {}, 输出图片URL: {}", taskId, taskResponse.getOutputImageUrl());
                return taskResponse;
            }

            // 任务失败
            if (TaskResponse.TaskStatus.FAILED.equals(status)) {
                log.error("任务执行失败，任务ID: {}, 错误信息: {}", taskId, taskResponse.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图像扩展任务失败: " + taskResponse.getMessage());
            }

            // 任务被取消
            if (TaskResponse.TaskStatus.CANCELED.equals(status)) {
                log.error("任务已被取消，任务ID: {}", taskId);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务已被取消");
            }

            // 任务状态未知
            if (TaskResponse.TaskStatus.UNKNOWN.equals(status)) {
                log.error("任务状态未知，任务ID: {}", taskId);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态未知");
            }

            // 任务处理中或排队中，等待后重试
            retryCount++;
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务查询被中断");
                }
            }
        }

        // 超过最大重试次数
        log.error("任务查询超时，任务ID: {}, 已重试次数: {}", taskId, maxRetries);
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务查询超时，请稍后重试");
    }
}

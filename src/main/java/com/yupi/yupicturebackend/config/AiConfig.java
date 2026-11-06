package com.yupi.yupicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 能力配置。按**用途**分块，不按供应商分：扩图（outpainting）与读图填信息（vision）
 * 现在各用一家（百炼 / DeepSeek），块名若还叫"某云 AI"就会误导读配置的人。
 * <p>
 * 供应商的域名、模型、凭证都在各自块里；同供应商时 vision 可以留空复用 outpainting 那一套。
 *
 * @author SPHZ
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AiConfig {

    /**
     * 画面扩展（阿里云百炼异步任务，见 docs/adr/0010）
     */
    private Outpainting outpainting = new Outpainting();

    /**
     * 读图填信息（AI 预填，见 docs/adr/0012）
     */
    private Vision vision = new Vision();

    @Data
    public static class Outpainting {

        /**
         * API Key（注意不要泄露）
         */
        private String apiKey;

        /**
         * 服务域名：只填主机名，不带路径后缀。业务空间专属域名形如
         * {WorkspaceId}.{region}.maas.aliyuncs.com，region 必须与 API Key 一致
         */
        private String baseUrl = "https://dashscope.aliyuncs.com";

        /**
         * 扩图模型名。仅可在同一异步协议（submit + /api/v1/tasks 轮询）下换变体，换协议要改路径
         */
        private String model = "image-out-painting";
    }

    /**
     * AI 预填的模型配置：换模型只改 model，换供应商填 baseUrl + apiKey（OpenAI 兼容端点即可）。
     * <p>
     * **必须是视觉模型**：文本模型（实测 deepseek-r1 / deepseek-v3）收到图片既不报错也不看图，
     * 直接回"我无法查看图片"后凭文本瞎编一组信息——这种失败是静默的，比报错危险。
     */
    @Data
    public static class Vision {

        /**
         * 模型名必须是该供应商已开通的视觉模型：写错或未开通会返回 model_not_found / access_denied，
         * 该错误要原样进日志，不能当成"AI 没读出内容"吞掉
         */
        private String model = "qwen-vl-plus";

        /**
         * OpenAI 兼容的对话补全路径
         */
        private String path = "/compatible-mode/v1/chat/completions";

        /**
         * 留空 = 复用 outpainting 的域名（两家都用百炼时最省事）；换供应商时必须与 apiKey 成对填
         */
        private String baseUrl = "";

        /**
         * 留空 = 复用 outpainting 的 Key。**与 baseUrl 必须成对**：只填一个会被代码拒绝，
         * 因为那要么是把凭据发到别的域名，要么是拿别家的 Key 调旧域名，两种都不该发生
         */
        private String apiKey = "";

        /**
         * 单张读图超时（毫秒）：按模型实测标定，换供应商要重标（见配置注释与 docs/adr/0012）
         */
        private long timeoutMs = 5000;
    }
}

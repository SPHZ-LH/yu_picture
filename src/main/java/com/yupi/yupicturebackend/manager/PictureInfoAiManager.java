package com.yupi.yupicturebackend.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.config.AiConfig;
import com.yupi.yupicturebackend.constant.PictureInfoConstant;
import com.yupi.yupicturebackend.model.vo.PictureAiPrefillVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 读图客户端：给一张可公网访问的图片，产出一组图片信息预填（名称/简介/分类/标签）。
 * <p>
 * 决策与护栏见 docs/adr/0012：
 * - **严格 JSON**：解析不出来就当没读出，绝不从自由文本里抠字段
 * - **词表封闭**：分类必须命中分类词表、标签只在标签词表内挑，越界即丢
 * - **失败静默**：超时/异常/模型写错名，一律返回 null，上传流程一个字都不受影响
 *
 * @author SPHZ
 */
@Service
@Slf4j
public class PictureInfoAiManager {

    private static final int MAX_NAME_LENGTH = 60;

    private static final int MAX_INTRODUCTION_LENGTH = 200;

    private static final int MAX_TAGS = 5;

    @Resource
    private AiConfig aiConfig;

    /**
     * 读图产出图片信息，读不出可用内容时返回 null（调用方按"没填"处理，不报错）
     */
    public PictureAiPrefillVO readPictureInfo(String imageUrl) {
        AiConfig.Vision vision = aiConfig.getVision();
        Endpoint endpoint = resolveEndpoint(vision);
        if (endpoint == null) {
            return null;
        }
        JSONObject body = JSONUtil.createObj()
                .set("model", vision.getModel())
                // 让服务端约束成 JSON：省掉"从散文里抠字段"这类不可靠解析
                .set("response_format", JSONUtil.createObj().set("type", "json_object"))
                .set("messages", JSONUtil.createArray().set(JSONUtil.createObj()
                        .set("role", "user")
                        .set("content", JSONUtil.createArray()
                                .set(JSONUtil.createObj()
                                        .set("type", "image_url")
                                        .set("image_url", JSONUtil.createObj().set("url", imageUrl)))
                                .set(JSONUtil.createObj().set("type", "text").set("text", buildPrompt())))));
        long start = System.currentTimeMillis();
        String content;
        try {
            HttpResponse response = HttpUtil.createPost(endpoint.baseUrl() + vision.getPath())
                    .header("Authorization", "Bearer " + endpoint.apiKey())
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout((int) vision.getTimeoutMs())
                    .execute();
            if (!response.isOk()) {
                // 换供应商后最容易踩的就是 401：响应体是纯文本（如 "Authentication Fails"），
                // 先看状态码再看报文，别让它掉进 JSON 解析异常那条笼统的日志里
                log.error("AI 预填调用失败, model={}, HTTP {}, 响应前 200 字={}", vision.getModel(),
                        response.getStatus(), StrUtil.subPre(response.body(), 200));
                return null;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            JSONObject error = result.getJSONObject("error");
            if (error != null) {
                // access_denied（模型名写错或未开通）要原样可见，不能混进"没读出内容"
                log.error("AI 预填调用失败, model={}, code={}, message={}", vision.getModel(),
                        error.getStr("code"), error.getStr("message"));
                return null;
            }
            content = result.getByPath("choices[0].message.content", String.class);
            // tokens 里含图片折算的额度：换成纯文本模型时这个数会明显偏小，是"没看图"的线索
            log.info("AI 预填读图完成, model={}, 耗时={}ms, tokens={}", vision.getModel(),
                    System.currentTimeMillis() - start, result.getByPath("usage.total_tokens"));
        } catch (Exception e) {
            log.warn("AI 预填调用异常, model={}, 耗时={}ms, 原因={}", vision.getModel(),
                    System.currentTimeMillis() - start, e.getMessage());
            return null;
        }
        return parseAndValidate(content, vision.getModel());
    }

    /**
     * 本次调用真正要用的域名与 Key。
     * <p>
     * 规则一句话：`vision.baseUrl` 与 `vision.apiKey` **必须成对**——都填了就是换供应商，
     * 都留空就复用 `outpainting` 那一套（两家都用百炼时省事）。只填一个直接拒绝：
     * 那要么是把凭据发到别的域名，要么是拿别家的 Key 去调旧域名，两种都不该发生。
     */
    private Endpoint resolveEndpoint(AiConfig.Vision vision) {
        boolean customBase = StrUtil.isNotBlank(vision.getBaseUrl());
        boolean customKey = StrUtil.isNotBlank(vision.getApiKey());
        if (customBase != customKey) {
            log.error("AI 预填配置不完整：vision.baseUrl 与 vision.apiKey 必须成对填写，"
                            + "或都留空以复用 outpainting 的配置；当前 baseUrl={}、apiKey={}",
                    customBase ? vision.getBaseUrl() : "（空）", customKey ? "（已填）" : "（空）");
            return null;
        }
        return customBase
                ? new Endpoint(vision.getBaseUrl(), vision.getApiKey())
                : new Endpoint(aiConfig.getOutpainting().getBaseUrl(), aiConfig.getOutpainting().getApiKey());
    }

    /**
     * 一次调用的目标：域名 + Key
     */
    private record Endpoint(String baseUrl, String apiKey) {
    }

    /**
     * 严格解析 + 词表校验：任何一处不合规都按"这个字段没读出"处理
     */
    private PictureAiPrefillVO parseAndValidate(String content, String model) {
        if (StrUtil.isBlank(content)) {
            log.warn("AI 预填返回空内容, model={}", model);
            return null;
        }
        JSONObject json;
        try {
            // 只清 BOM 与首尾空白，不做语义抢救：不是纯 JSON 就整体丢弃
            json = JSONUtil.parseObj(StrUtil.trim(content).replace("\uFEFF", ""));
        } catch (Exception e) {
            log.warn("AI 预填返回的不是 JSON，已丢弃, model={}, 原文前 200 字={}", model, StrUtil.subPre(content, 200));
            return null;
        }
        PictureAiPrefillVO vo = new PictureAiPrefillVO();
        vo.setName(cleanText(json.getStr("name"), MAX_NAME_LENGTH));
        vo.setIntroduction(cleanText(json.getStr("introduction"), MAX_INTRODUCTION_LENGTH));
        String category = cleanText(json.getStr("category"), 32);
        vo.setCategory(category != null && PictureInfoConstant.CATEGORY_LIST.contains(category) ? category : null);
        vo.setTags(cleanTags(json.get("tags")));
        if (vo.getName() == null && vo.getIntroduction() == null && vo.getCategory() == null
                && (vo.getTags() == null || vo.getTags().isEmpty())) {
            log.warn("AI 预填没有产出可用字段, model={}", model);
            return null;
        }
        return vo;
    }

    private String cleanText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    /**
     * 标签只留词表内的：模型爱造新词（"梦幻/海底/云海"），而这一列是筛选器的取值域
     */
    private List<String> cleanTags(Object raw) {
        if (!(raw instanceof JSONArray array)) {
            return null;
        }
        Set<String> keep = new LinkedHashSet<>();
        for (Object item : array) {
            String tag = item == null ? null : cleanText(item.toString(), 32);
            if (tag != null && PictureInfoConstant.TAG_LIST.contains(tag)) {
                keep.add(tag);
            }
            if (keep.size() >= MAX_TAGS) {
                break;
            }
        }
        return keep.isEmpty() ? null : new ArrayList<>(keep);
    }

    private String buildPrompt() {
        return "你是图库的图片信息标注助手。看图后只输出一个 JSON 对象，字段含义如下：\n"
                + "- name：不超过 20 个字的中文短名，概括画面主体，不要书名号或引号\n"
                + "- introduction：不超过 60 个字的中文简介，一句话说清画面\n"
                + "- category：只能从 " + PictureInfoConstant.CATEGORY_LIST + " 里选一个；拿不准就给空字符串\n"
                + "- tags：只能从 " + PictureInfoConstant.TAG_LIST + " 里挑，最多 5 个，按与画面的相关性排序；"
                + "拿不准就少给或给空数组\n"
                + "规则：只输出 JSON，不要解释、不要 markdown 代码块；看不清图片内容时所有字段都留空。";
    }
}

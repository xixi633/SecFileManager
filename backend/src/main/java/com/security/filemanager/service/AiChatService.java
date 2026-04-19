package com.security.filemanager.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.security.filemanager.dto.AiChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SEARCH_PREFIX = "__SEARCH__:";

    @Value("${secure-file.ai.api-key}")
    private String apiKey;

    @Value("${secure-file.ai.model}")
    private String model;

    @Value("${secure-file.ai.system-prompt}")
    private String systemPrompt;

    private static final Pattern FILE_SEARCH_PATTERN = Pattern.compile(
            "(查找|搜索|找|搜|寻找|查一下|帮我找|有没有|列出|显示|看看|查看|哪些|什么).{0,4}文件",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TYPE_SEARCH_PATTERN = Pattern.compile(
            "(java|python|c\\+\\+|javascript|typescript|html|css|json|xml|sql|doc|docx|pdf|txt|excel|xlsx|ppt|pptx|zip|rar|7z|jpg|png|gif|mp4|mp3).{0,2}(文件|格式|类型)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STANDALONE_TYPE_PATTERN = Pattern.compile(
            "^\\s*(java|python|c\\+\\+|javascript|typescript|html|css|json|xml|sql|doc|docx|pdf|txt|excel|xlsx|ppt|pptx|zip|rar|7z|jpg|png|gif|mp4|mp3|图片|视频|音频|文档|代码|压缩包|文本|目录|文件夹|所有文件|全部文件)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SEARCH_VERB_PATTERN = Pattern.compile(
            "(查找|搜索|找|搜|寻找|查|帮我|有没有|列出|显示|看看|查看|给我|需要|要|拿)");

    private static final Pattern FILE_NOUN_PATTERN = Pattern.compile(
            "(文件|文档|报告|代码|图片|照片|视频|音频|压缩包|文件夹|目录|资料|作业|课件|论文|笔记|项目|实验报告|实验)");

    private static final Pattern TYPE_WORD_PATTERN = Pattern.compile(
            "(java|python|c\\+\\+|javascript|typescript|html|css|json|xml|sql|doc|docx|pdf|txt|excel|xlsx|ppt|pptx|zip|rar|7z|jpg|png|gif|mp4|mp3|图片|视频|音频|文档|代码|压缩包|文本|目录|文件夹)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RELATED_PATTERN = Pattern.compile(
            "(.+?)(相关|有关|之类|之类的)");

    private static final Pattern NOISE_PATTERN = Pattern.compile(
            "^(我|我的|所有|全部|一下|下|那些|这些|那个|这个|个|些|帮|帮忙|请|能|可以|可|会|想|要|得)");

    private static final Pattern TRAILING_NOISE_PATTERN = Pattern.compile(
            "(相关|有关|之类|之类的|等|等的|类型|格式|文件|的|一下|下)$");

    private static final Map<String, String> TYPE_CATEGORY_MAP = new HashMap<>();
    private static final Map<String, String> CHINESE_TYPE_MAP = new HashMap<>();

    static {
        TYPE_CATEGORY_MAP.put("java", "code");
        TYPE_CATEGORY_MAP.put("python", "code");
        TYPE_CATEGORY_MAP.put("c++", "code");
        TYPE_CATEGORY_MAP.put("javascript", "code");
        TYPE_CATEGORY_MAP.put("typescript", "code");
        TYPE_CATEGORY_MAP.put("html", "code");
        TYPE_CATEGORY_MAP.put("css", "code");
        TYPE_CATEGORY_MAP.put("json", "code");
        TYPE_CATEGORY_MAP.put("xml", "code");
        TYPE_CATEGORY_MAP.put("sql", "code");
        TYPE_CATEGORY_MAP.put("doc", "document");
        TYPE_CATEGORY_MAP.put("docx", "document");
        TYPE_CATEGORY_MAP.put("pdf", "document");
        TYPE_CATEGORY_MAP.put("txt", "text");
        TYPE_CATEGORY_MAP.put("excel", "document");
        TYPE_CATEGORY_MAP.put("xlsx", "document");
        TYPE_CATEGORY_MAP.put("ppt", "document");
        TYPE_CATEGORY_MAP.put("pptx", "document");
        TYPE_CATEGORY_MAP.put("zip", "archive");
        TYPE_CATEGORY_MAP.put("rar", "archive");
        TYPE_CATEGORY_MAP.put("7z", "archive");
        TYPE_CATEGORY_MAP.put("jpg", "image");
        TYPE_CATEGORY_MAP.put("png", "image");
        TYPE_CATEGORY_MAP.put("gif", "image");
        TYPE_CATEGORY_MAP.put("mp4", "video");
        TYPE_CATEGORY_MAP.put("mp3", "audio");

        CHINESE_TYPE_MAP.put("图片", "image");
        CHINESE_TYPE_MAP.put("视频", "video");
        CHINESE_TYPE_MAP.put("音频", "audio");
        CHINESE_TYPE_MAP.put("文档", "document");
        CHINESE_TYPE_MAP.put("代码", "code");
        CHINESE_TYPE_MAP.put("压缩包", "archive");
        CHINESE_TYPE_MAP.put("文本", "text");
        CHINESE_TYPE_MAP.put("目录", "folder");
        CHINESE_TYPE_MAP.put("文件夹", "folder");
        CHINESE_TYPE_MAP.put("所有文件", null);
        CHINESE_TYPE_MAP.put("全部文件", null);
    }

    public void streamChat(List<AiChatRequest.MessageItem> messages, Long userId, SseEmitter emitter) {
        new Thread(() -> {
            try {
                String lastUserMsg = null;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if ("user".equals(messages.get(i).getRole())) {
                        lastUserMsg = messages.get(i).getContent();
                        break;
                    }
                }
                if (lastUserMsg != null && isFileSearchIntent(lastUserMsg)) {
                    handleFileSearch(lastUserMsg, emitter);
                } else {
                    handleNormalChat(messages, emitter);
                }
            } catch (Exception e) {
                log.error("AI chat error", e);
                String errMsg = "抱歉，AI 服务暂时不可用，请稍后再试。";
                if (e.getMessage() != null && e.getMessage().contains("FreeTierOnly")) {
                    errMsg = "AI 服务的免费额度已用完，暂时无法对话，但文件搜索功能仍可正常使用。";
                }
                try {
                    emitter.send(SseEmitter.event().data(errMsg));
                } catch (IOException ignored) {
                }
                emitter.complete();
            }
        }).start();
    }

    private boolean isFileSearchIntent(String msg) {
        if (FILE_SEARCH_PATTERN.matcher(msg).find())
            return true;
        if (TYPE_SEARCH_PATTERN.matcher(msg).find())
            return true;
        if (STANDALONE_TYPE_PATTERN.matcher(msg).find())
            return true;
        if (FILE_NOUN_PATTERN.matcher(msg).find() && TYPE_WORD_PATTERN.matcher(msg).find())
            return true;
        if (FILE_NOUN_PATTERN.matcher(msg).find() && SEARCH_VERB_PATTERN.matcher(msg).find())
            return true;
        if (RELATED_PATTERN.matcher(msg).find())
            return true;
        if (SEARCH_VERB_PATTERN.matcher(msg).find() && TYPE_WORD_PATTERN.matcher(msg).find())
            return true;
        if (msg.contains("文件夹") || msg.contains("目录"))
            return true;
        return false;
    }

    private String extractKeyword(String msg) {
        var m = FILE_SEARCH_PATTERN.matcher(msg);
        if (m.find()) {
            String afterVerb = msg.substring(m.start())
                    .replaceFirst("^(查找|搜索|找|搜|寻找|查一下|帮我找|有没有|列出|显示|看看|查看|哪些|什么)", "").trim();
            String kw = cleanKeyword(afterVerb);
            if (kw != null)
                return kw;
        }

        var rm = RELATED_PATTERN.matcher(msg);
        if (rm.find()) {
            return cleanKeyword(rm.group(1));
        }

        if (FILE_NOUN_PATTERN.matcher(msg).find()) {
            String cleaned = msg;
            cleaned = cleaned.replaceAll("(查找|搜索|找|搜|寻找|查一下|帮我找|有没有|列出|显示|看看|查看|给我|需要|要|一下|我|的|关于|包含|含有|个|些|那种|这种)",
                    "");
            cleaned = cleaned.replaceAll("(文件|文档|报告|代码|图片|照片|视频|音频|压缩包|文件夹|目录|资料|作业|课件|论文|笔记|项目|实验报告|实验)", "");
            cleaned = cleanKeyword(cleaned);
            if (cleaned != null)
                return cleaned;
        }

        var tm = TYPE_SEARCH_PATTERN.matcher(msg);
        if (tm.find()) {
            return cleanKeyword(tm.group(1));
        }

        var sm = STANDALONE_TYPE_PATTERN.matcher(msg);
        if (sm.find()) {
            return cleanKeyword(sm.group(1));
        }

        return cleanKeyword(msg);
    }

    private String extractTypeCategory(String msg) {
        var sm = STANDALONE_TYPE_PATTERN.matcher(msg);
        if (sm.find()) {
            String type = sm.group(1).toLowerCase();
            if ("所有文件".equals(type) || "全部文件".equals(type))
                return null;
            if (CHINESE_TYPE_MAP.containsKey(type))
                return CHINESE_TYPE_MAP.get(type);
            return TYPE_CATEGORY_MAP.getOrDefault(type, null);
        }

        if (msg.contains("文件夹") || msg.contains("目录")) {
            String cleaned = msg.replaceAll("(文件夹|目录|的|所有|全部|一下|下)", "").trim();
            if (cleaned.isBlank())
                return "folder";
        }

        var tm = TYPE_SEARCH_PATTERN.matcher(msg);
        if (tm.find()) {
            String type = tm.group(1).toLowerCase();
            if (CHINESE_TYPE_MAP.containsKey(type))
                return CHINESE_TYPE_MAP.get(type);
            return TYPE_CATEGORY_MAP.getOrDefault(type, null);
        }
        return null;
    }

    private String cleanKeyword(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String cleaned = raw.trim();
        cleaned = NOISE_PATTERN.matcher(cleaned).replaceAll("").trim();
        cleaned = TRAILING_NOISE_PATTERN.matcher(cleaned).replaceAll("").trim();
        cleaned = cleaned.replaceAll("^\\.+", "");
        cleaned = cleaned.replaceAll("^(我|我的|所有|全部|一下|下|个|些|那些|这些|那个|这个|帮|帮忙|请|能|可以|可|会|想|要|得)", "").trim();
        cleaned = cleaned.replaceAll("(相关|有关|之类|之类的|等|等的|类型|格式|文件|的|一下|下)$", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private void handleFileSearch(String userMsg, SseEmitter emitter) throws Exception {
        String keyword = extractKeyword(userMsg);
        String typeCategory = extractTypeCategory(userMsg);
        boolean isStandaloneType = STANDALONE_TYPE_PATTERN.matcher(userMsg.trim()).find();

        String searchKeyword = null;
        String searchTypeCategory = null;

        if (isStandaloneType) {
            searchTypeCategory = typeCategory;
        } else {
            searchKeyword = keyword;
        }

        log.info("AI search command: msg='{}', keyword='{}', typeCategory='{}', isStandalone={}",
                userMsg, searchKeyword, searchTypeCategory, isStandaloneType);

        Map<String, String> searchParams = new HashMap<>();
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            searchParams.put("keyword", searchKeyword);
        }
        if (searchTypeCategory != null && !searchTypeCategory.isBlank()) {
            searchParams.put("typeCategory", searchTypeCategory);
        }

        String jsonParams = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(searchParams);
        emitter.send(SseEmitter.event().data(SEARCH_PREFIX + jsonParams));

        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }

    private void handleNormalChat(List<AiChatRequest.MessageItem> messages, SseEmitter emitter) throws Exception {
        List<Message> dashMessages = new ArrayList<>();
        dashMessages.add(Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build());

        for (AiChatRequest.MessageItem item : messages) {
            String role = "user".equals(item.getRole()) ? Role.USER.getValue() : Role.ASSISTANT.getValue();
            dashMessages.add(Message.builder().role(role).content(item.getContent()).build());
        }

        Generation gen = new Generation();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(dashMessages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)
                .build();

        gen.streamCall(param).blockingForEach(result -> {
            var choices = result.getOutput().getChoices();
            if (choices != null && !choices.isEmpty()) {
                var choice = choices.get(0);
                var msg = choice.getMessage();
                if (msg != null && msg.getContent() != null && !msg.getContent().isEmpty()) {
                    emitter.send(SseEmitter.event().data(msg.getContent()));
                }
            }
        });

        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }
}

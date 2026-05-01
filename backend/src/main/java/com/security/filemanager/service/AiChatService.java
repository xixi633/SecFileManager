package com.security.filemanager.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.filemanager.dto.AiChatRequest;
import com.security.filemanager.entity.FileInfo;
import com.security.filemanager.mapper.FileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SEARCH_PREFIX = "__SEARCH__:";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DETAIL_ROWS = 12;
    private static final int DEFAULT_ORDER_RESULT_LIMIT = 5;
    private static final int MAX_ORDER_RESULT_LIMIT = 20;

    private static final long DELETE_CONFIRM_TTL_MILLIS = 10 * 60 * 1000L;
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "avi", "mov", "wmv", "mkv", "webm", "m4v");
    private static final Set<String> AUDIO_EXTS = Set.of("mp3", "wav", "flac", "aac", "ogg", "m4a");
    private static final Set<String> TEXT_EXTS = Set.of("txt", "md", "rtf", "log", "ini", "conf", "yaml", "yml", "json",
            "xml");

    private static final Pattern DELETE_CONFIRM_PATTERN = Pattern.compile(
            "(确认删除|确认|确定删除|确定|是的|对|没错|删吧|可以删|就删这个)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DELETE_CANCEL_PATTERN = Pattern.compile(
            "(取消|不删了|先不删|先别删|不用了|算了|否|不要删)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> CODE_EXTS = Set.of("java", "js", "ts", "jsx", "tsx", "py", "go", "c", "cpp", "h",
            "hpp", "cs", "php", "rb", "rs", "kt", "swift", "vue", "html", "css", "scss", "sql", "sh", "bat", "ps1");
    private static final Set<String> ARCHIVE_EXTS = Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz");
    private static final Set<String> DOCUMENT_EXTS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv");

    private final Map<Long, PendingDeleteContext> pendingDeleteMap = new ConcurrentHashMap<>();

    @Resource
    private FileMapper fileMapper;

    @Resource
    private FileService fileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class PendingDeleteContext {
        private final Long fileId;
        private final String fileName;
        private final int score;
        private final long createdAt;

        private PendingDeleteContext(Long fileId, String fileName, int score, long createdAt) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.score = score;
            this.createdAt = createdAt;
        }
    }

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

    private static final Pattern DELETE_INTENT_PATTERN = Pattern.compile(
            "(删除|删掉|移除|清理|扔掉|去掉|干掉|不要了|废弃|丢掉|放到回收站|回收站).{0,12}(文件|文档|目录|文件夹)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HOW_TO_PATTERN = Pattern.compile(
            "(怎么|如何|怎样|教程|步骤)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FILE_INFO_INTENT_PATTERN = Pattern.compile(
            "(详情|详细|信息|大小|总大小|占用|上传|下载|哈希|统计|数量|多少个|多少|清单|列表|最近|最新|最早|最旧|最老|最久|最大|最小|类型)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DETAIL_REQUEST_PATTERN = Pattern.compile(
            "(详情|详细|信息|清单|列表|列出|具体)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RECENT_REQUEST_PATTERN = Pattern.compile(
            "(最近|最新)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LARGEST_REQUEST_PATTERN = Pattern.compile(
            "(最大|最占空间|最占用)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SMALLEST_REQUEST_PATTERN = Pattern.compile(
            "(最小|最不占空间|最少占用)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OLDEST_REQUEST_PATTERN = Pattern.compile(
            "(最早|最旧|最老|最久|最先上传|最早上传|最先)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORDER_LIMIT_PATTERN = Pattern.compile(
            "(?:前|后|top|TOP)?\\s*(\\d{1,2})\\s*(?:个|条|份|项|个文件|条文件)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SEVERAL_REQUEST_PATTERN = Pattern.compile(
            "(几个|几条|几份|一些|若干)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORDER_NOISE_PATTERN = Pattern.compile(
            "(最近|最新|最早|最旧|最老|最久|最先上传|最早上传|最先|最大|最小|最占空间|最占用|最不占空间|最少占用|按大小|从大到小|从小到大|上传|下载)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED_NAME_PATTERN = Pattern.compile("[\"“'《](.+?)[\"”'》]");

    private static final Pattern FILE_NAME_HINT_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9_\\-]+\\.[A-Za-z0-9]{1,8})");

    private static final Pattern FILE_ID_HINT_PATTERN = Pattern.compile(
            "(?:文件?\\s*id|编号|id)\\s*[:：=]?\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z0-9._-]{2,}");

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");

    private static final Set<String> QUERY_STOP_TERMS = Set.of(
            "删除", "删掉", "移除", "清理", "扔掉", "去掉", "干掉", "不要了", "废弃", "丢掉", "回收站",
            "文件", "文档", "目录", "文件夹", "资料", "数据", "帮我", "帮忙", "请", "麻烦", "一下", "给我",
            "查找", "搜索", "寻找", "查询", "查看", "看看", "显示", "列出", "有没有", "哪些", "什么", "找到",
            "详情", "详细", "信息", "统计", "大小", "类型", "数量", "多少", "最近", "最新", "最大", "最小",
            "这个", "那个", "这些", "那些", "我的", "全部", "所有", "一下子", "以及", "还有");

    private static final Map<String, String> TERM_CATEGORY_MAP = new HashMap<>();

    private enum IntentType {
        DELETE,
        INFO,
        SEARCH,
        CHAT
    }

    private static class FileScore {
        private final FileInfo file;
        private final int score;

        private FileScore(FileInfo file, int score) {
            this.file = file;
            this.score = score;
        }
    }

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

        TERM_CATEGORY_MAP.put("图片", "image");
        TERM_CATEGORY_MAP.put("照片", "image");
        TERM_CATEGORY_MAP.put("截图", "image");
        TERM_CATEGORY_MAP.put("视频", "video");
        TERM_CATEGORY_MAP.put("音频", "audio");
        TERM_CATEGORY_MAP.put("音乐", "audio");
        TERM_CATEGORY_MAP.put("文档", "document");
        TERM_CATEGORY_MAP.put("报告", "document");
        TERM_CATEGORY_MAP.put("表格", "document");
        TERM_CATEGORY_MAP.put("代码", "code");
        TERM_CATEGORY_MAP.put("脚本", "code");
        TERM_CATEGORY_MAP.put("压缩包", "archive");
        TERM_CATEGORY_MAP.put("文本", "text");
        TERM_CATEGORY_MAP.put("目录", "folder");
        TERM_CATEGORY_MAP.put("文件夹", "folder");

        TYPE_CATEGORY_MAP.forEach((k, v) -> {
            if (v != null) {
                TERM_CATEGORY_MAP.put(k, v);
            }
        });
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
                if (lastUserMsg != null) {
                    if (tryHandlePendingDeleteConfirmation(lastUserMsg, userId, emitter)) {
                        return;
                    }

                    IntentType intent = detectIntent(lastUserMsg);
                    log.info("AI intent detected: intent={}, msg='{}'", intent, lastUserMsg);
                    switch (intent) {
                        case DELETE:
                            handleFileDelete(lastUserMsg, userId, emitter);
                            return;
                        case INFO:
                            handleFileInfoQuery(lastUserMsg, userId, emitter);
                            return;
                        case SEARCH:
                            handleFileSearch(lastUserMsg, userId, emitter);
                            return;
                        case CHAT:
                        default:
                            break;
                    }
                }

                handleNormalChat(messages, emitter);
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

    private IntentType detectIntent(String msg) {
        if (msg == null || msg.isBlank()) {
            return IntentType.CHAT;
        }

        String trimmed = msg.trim();
        int deleteScore = calculateDeleteIntentScore(trimmed);
        int infoScore = calculateInfoIntentScore(trimmed);
        int searchScore = calculateSearchIntentScore(trimmed);

        if (deleteScore >= 4 && deleteScore >= infoScore + 1 && deleteScore >= searchScore + 1) {
            return IntentType.DELETE;
        }
        if (infoScore >= 3 && infoScore >= searchScore) {
            return IntentType.INFO;
        }
        if (searchScore >= 2) {
            return IntentType.SEARCH;
        }
        if (infoScore >= 2) {
            return IntentType.INFO;
        }
        if (deleteScore >= 2) {
            return IntentType.DELETE;
        }
        return IntentType.CHAT;
    }

    private int calculateDeleteIntentScore(String msg) {
        if (msg == null || msg.isBlank()) {
            return 0;
        }
        if (HOW_TO_PATTERN.matcher(msg).find()) {
            return 0;
        }

        int score = 0;
        if (DELETE_INTENT_PATTERN.matcher(msg).find()) {
            score += 4;
        }
        if (msg.contains("回收站")) {
            score += 2;
        }
        if (extractFileNameHint(msg) != null || extractFileIdHint(msg) != null) {
            score += 1;
        }
        if (msg.contains("删") || msg.contains("删除") || msg.contains("移除") || msg.contains("不要了")) {
            score += 1;
        }
        return score;
    }

    private int calculateInfoIntentScore(String msg) {
        if (msg == null || msg.isBlank()) {
            return 0;
        }
        int score = 0;
        if (FILE_INFO_INTENT_PATTERN.matcher(msg).find()) {
            score += 2;
        }
        if (FILE_NOUN_PATTERN.matcher(msg).find() || TYPE_WORD_PATTERN.matcher(msg).find()) {
            score += 1;
        }
        if (isOrderRequest(msg)) {
            score += 1;
        }
        return score;
    }

    private int calculateSearchIntentScore(String msg) {
        if (msg == null || msg.isBlank()) {
            return 0;
        }
        int score = 0;
        if (FILE_SEARCH_PATTERN.matcher(msg).find() || SEARCH_VERB_PATTERN.matcher(msg).find()) {
            score += 2;
        }
        if (TYPE_SEARCH_PATTERN.matcher(msg).find() || STANDALONE_TYPE_PATTERN.matcher(msg).find()) {
            score += 1;
        }
        if (FILE_NOUN_PATTERN.matcher(msg).find()) {
            score += 1;
        }
        if (msg.contains("定位") || msg.contains("在哪") || msg.contains("有没有")) {
            score += 1;
        }
        return score;
    }

    private boolean isDeleteIntent(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        if (HOW_TO_PATTERN.matcher(msg).find()) {
            return false;
        }
        return DELETE_INTENT_PATTERN.matcher(msg).find();
    }

    private boolean isFileInfoIntent(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        if (!FILE_INFO_INTENT_PATTERN.matcher(msg).find()) {
            return false;
        }
        return FILE_NOUN_PATTERN.matcher(msg).find() || TYPE_WORD_PATTERN.matcher(msg).find();
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

    private String extractDeleteKeyword(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }

        String fileNameHint = extractFileNameHint(msg);
        if (fileNameHint != null) {
            return fileNameHint;
        }

        Matcher quoted = QUOTED_NAME_PATTERN.matcher(msg);
        if (quoted.find()) {
            String candidate = cleanKeyword(quoted.group(1));
            if (candidate != null) {
                return candidate;
            }
        }

        String cleaned = msg
                .replaceAll("(请|帮我|麻烦|把|将|一下|一下子|给我|可以|能否|我要|我想|帮忙)", "")
                .replaceAll("(删除|删掉|移除|清理|扔掉|去掉|干掉|不要了|废弃|丢掉|放到回收站|回收站)", "")
                .replaceAll("(文件|文档|目录|文件夹|这个|那个|这些|那些)", "")
                .replaceAll("[,，。！？!?：:]", " ")
                .trim();

        return cleanKeyword(cleaned);
    }

    private String extractOrderAwareKeyword(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }

        String cleaned = msg
                .replaceAll("(查找|搜索|找|搜|寻找|查一下|帮我找|有没有|列出|显示|看看|查看|给我|需要|要|删除|删掉|移除|清理|扔掉|去掉|干掉|不要了|废弃|丢掉|放到回收站|回收站)",
                        " ")
                .replaceAll("(请|帮我|麻烦|把|将|一下|一下子|给我|我要|我想|帮忙|这个|那个|这些|那些|文件|文件夹|目录)", " ")
                .replaceAll("[,，。！？!?：:]", " ");
        cleaned = ORDER_NOISE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
        return cleanKeyword(cleaned);
    }

    private String extractFileNameHint(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }
        Matcher matcher = FILE_NAME_HINT_PATTERN.matcher(msg);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Long extractFileIdHint(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }
        Matcher matcher = FILE_ID_HINT_PATTERN.matcher(msg);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> extractQueryTerms(String userMsg, String keyword, String fileNameHint) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addTermIfMeaningful(terms, keyword);
        addTermIfMeaningful(terms, fileNameHint);

        if (userMsg == null || userMsg.isBlank()) {
            return new ArrayList<>(terms);
        }

        Matcher quoted = QUOTED_NAME_PATTERN.matcher(userMsg);
        while (quoted.find()) {
            addTermIfMeaningful(terms, quoted.group(1));
        }

        Matcher matcher = TOKEN_PATTERN.matcher(userMsg.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            addTermIfMeaningful(terms, matcher.group());
        }

        return new ArrayList<>(terms);
    }

    private void addTermIfMeaningful(Set<String> terms, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String term = raw.trim().toLowerCase(Locale.ROOT);
        if (!isMeaningfulTerm(term)) {
            return;
        }
        terms.add(term);
    }

    private boolean isMeaningfulTerm(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        String trimmed = term.trim();
        if (trimmed.length() < 2) {
            return false;
        }
        return !QUERY_STOP_TERMS.contains(trimmed);
    }

    private String normalizeForMatch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return NORMALIZE_PATTERN.matcher(input.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private String getFileBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String inferTypeCategoryByTerm(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        if (TERM_CATEGORY_MAP.containsKey(lowerTerm)) {
            return TERM_CATEGORY_MAP.get(lowerTerm);
        }
        return CHINESE_TYPE_MAP.getOrDefault(lowerTerm, null);
    }

    private int levenshteinDistance(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        int aLen = a.length();
        int bLen = b.length();
        if (aLen == 0) {
            return bLen;
        }
        if (bLen == 0) {
            return aLen;
        }

        int[] prev = new int[bLen + 1];
        int[] curr = new int[bLen + 1];

        for (int j = 0; j <= bLen; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= aLen; i++) {
            curr[0] = i;
            char aChar = a.charAt(i - 1);
            for (int j = 1; j <= bLen; j++) {
                int cost = aChar == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }

        return prev[bLen];
    }

    private double similarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0D;
        }
        String a = left.length() > 96 ? left.substring(0, 96) : left;
        String b = right.length() > 96 ? right.substring(0, 96) : right;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1D;
        }
        int distance = levenshteinDistance(a, b);
        return 1D - ((double) distance / maxLen);
    }

    private int calculateFileMatchScore(FileInfo file, String keyword, List<String> terms) {
        if (file == null) {
            return 0;
        }

        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String baseName = getFileBaseName(lowerName);
        String description = Optional.ofNullable(file.getDescription()).orElse("").toLowerCase(Locale.ROOT);

        String normalizedName = normalizeForMatch(lowerName);
        String normalizedBaseName = normalizeForMatch(baseName);
        String normalizedDesc = normalizeForMatch(description);

        String normalizedKeyword = normalizeForMatch(keyword);
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            if (lowerName.equalsIgnoreCase(keyword) || normalizedName.equals(normalizedKeyword)
                    || normalizedBaseName.equals(normalizedKeyword)) {
                score += 1000;
            }
            if (normalizedName.contains(normalizedKeyword) || normalizedBaseName.contains(normalizedKeyword)) {
                score += 520;
            }
            if (normalizedDesc.contains(normalizedKeyword)) {
                score += 260;
            }
            if (normalizedKeyword.contains(normalizedBaseName) && normalizedBaseName.length() >= 4) {
                score += 220;
            }

            double similarity = Math.max(
                    this.similarity(normalizedKeyword, normalizedName),
                    this.similarity(normalizedKeyword, normalizedBaseName));
            if (similarity >= 0.9D) {
                score += 420;
            } else if (similarity >= 0.8D) {
                score += 260;
            } else if (similarity >= 0.7D) {
                score += 160;
            } else if (similarity >= 0.6D && normalizedKeyword.length() >= 4) {
                score += 80;
            }
        }

        String fileCategory = resolveTypeCategory(file);
        for (String term : terms) {
            String normalizedTerm = normalizeForMatch(term);
            if (normalizedTerm.isBlank()) {
                continue;
            }

            if (normalizedName.contains(normalizedTerm) || normalizedBaseName.contains(normalizedTerm)) {
                score += 120;
            } else if (normalizedDesc.contains(normalizedTerm)) {
                score += 55;
            } else {
                double termSimilarity = Math.max(
                        similarity(normalizedTerm, normalizedName),
                        similarity(normalizedTerm, normalizedBaseName));
                if (termSimilarity >= 0.85D) {
                    score += 100;
                } else if (termSimilarity >= 0.75D && normalizedTerm.length() >= 3) {
                    score += 70;
                }
            }

            String inferredCategory = inferTypeCategoryByTerm(term);
            if (inferredCategory != null && inferredCategory.equals(fileCategory)) {
                score += 60;
            }
        }

        return score;
    }

    private List<FileScore> rankFilesByQuery(List<FileInfo> files, String userMsg, String keyword, String typeCategory,
            int minScore) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        String fileNameHint = extractFileNameHint(userMsg);
        List<String> terms = extractQueryTerms(userMsg, keyword, fileNameHint);
        boolean hasQuery = (keyword != null && !keyword.isBlank()) || !terms.isEmpty();

        List<FileScore> scoredFiles = files.stream()
                .filter(file -> {
                    if (typeCategory == null || typeCategory.isBlank()) {
                        return true;
                    }
                    return typeCategory.equals(resolveTypeCategory(file));
                })
                .map(file -> new FileScore(file,
                        hasQuery ? calculateFileMatchScore(file, keyword, terms) : 1))
                .filter(item -> !hasQuery || item.score >= minScore)
                .sorted((left, right) -> {
                    int compareScore = Integer.compare(right.score, left.score);
                    if (compareScore != 0) {
                        return compareScore;
                    }
                    return Comparator.comparing(FileInfo::getUploadTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                            .compare(left.file, right.file);
                })
                .collect(Collectors.toList());

        if (!scoredFiles.isEmpty() || !hasQuery) {
            return scoredFiles;
        }

        // 降低阈值兜底，避免自然语言有噪声时完全匹配失败。
        return files.stream()
                .filter(file -> {
                    if (typeCategory == null || typeCategory.isBlank()) {
                        return true;
                    }
                    return typeCategory.equals(resolveTypeCategory(file));
                })
                .map(file -> new FileScore(file, calculateFileMatchScore(file, keyword, terms)))
                .filter(item -> item.score >= Math.max(45, minScore / 2))
                .sorted((left, right) -> Integer.compare(right.score, left.score))
                .collect(Collectors.toList());
    }

    private boolean isOrderRequest(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        return RECENT_REQUEST_PATTERN.matcher(msg).find()
                || OLDEST_REQUEST_PATTERN.matcher(msg).find()
                || LARGEST_REQUEST_PATTERN.matcher(msg).find()
                || SMALLEST_REQUEST_PATTERN.matcher(msg).find();
    }

    private int extractOrderResultLimit(String msg) {
        if (msg == null || msg.isBlank()) {
            return DEFAULT_ORDER_RESULT_LIMIT;
        }

        if (msg.contains("全部") || msg.contains("所有")) {
            return MAX_ORDER_RESULT_LIMIT;
        }

        Matcher matcher = ORDER_LIMIT_PATTERN.matcher(msg);
        if (matcher.find()) {
            try {
                int parsed = Integer.parseInt(matcher.group(1));
                return Math.max(1, Math.min(MAX_ORDER_RESULT_LIMIT, parsed));
            } catch (NumberFormatException ignored) {
            }
        }

        if (SEVERAL_REQUEST_PATTERN.matcher(msg).find()) {
            return 3;
        }

        return DEFAULT_ORDER_RESULT_LIMIT;
    }

    private String getOrderRequestLabel(String msg) {
        if (msg == null || msg.isBlank()) {
            return "默认顺序";
        }
        if (RECENT_REQUEST_PATTERN.matcher(msg).find()) {
            return "最近上传";
        }
        if (OLDEST_REQUEST_PATTERN.matcher(msg).find()) {
            return "最早上传";
        }
        if (LARGEST_REQUEST_PATTERN.matcher(msg).find()) {
            return "文件大小从大到小";
        }
        if (SMALLEST_REQUEST_PATTERN.matcher(msg).find()) {
            return "文件大小从小到大";
        }
        return "默认顺序";
    }

    private Comparator<FileInfo> buildOrderComparator(String msg) {
        if (msg != null && RECENT_REQUEST_PATTERN.matcher(msg).find()) {
            return Comparator.comparing(FileInfo::getUploadTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if (msg != null && OLDEST_REQUEST_PATTERN.matcher(msg).find()) {
            return Comparator.comparing(FileInfo::getUploadTime, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (msg != null && LARGEST_REQUEST_PATTERN.matcher(msg).find()) {
            return Comparator.comparing(
                    (FileInfo file) -> file.getFileSize() != null ? file.getFileSize() : 0L,
                    Comparator.reverseOrder());
        }
        if (msg != null && SMALLEST_REQUEST_PATTERN.matcher(msg).find()) {
            return Comparator.comparing(file -> file.getFileSize() != null ? file.getFileSize() : 0L);
        }
        return Comparator.comparing(FileInfo::getUploadTime, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<FileInfo> selectOrderedFiles(List<FileInfo> files, String typeCategory, String keyword,
            String userMsg) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        List<FileInfo> candidates;
        if (keyword != null && !keyword.isBlank()) {
            candidates = rankFilesByQuery(files, userMsg, keyword, typeCategory, 45).stream()
                    .map(item -> item.file)
                    .collect(Collectors.toList());
        } else {
            candidates = files.stream()
                    .filter(file -> typeCategory == null || typeCategory.isBlank()
                            || typeCategory.equals(resolveTypeCategory(file)))
                    .collect(Collectors.toList());
        }

        Comparator<FileInfo> comparator = buildOrderComparator(userMsg)
                .thenComparing(FileInfo::getUploadTime, Comparator.nullsLast(Comparator.reverseOrder()));
        return candidates.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private void handleFileSearch(String userMsg, Long userId, SseEmitter emitter) throws Exception {
        String fileNameHint = extractFileNameHint(userMsg);
        boolean isOrderRequest = isOrderRequest(userMsg);
        String keyword = fileNameHint != null ? fileNameHint
                : (isOrderRequest ? extractOrderAwareKeyword(userMsg) : extractKeyword(userMsg));
        String typeCategory = extractTypeCategory(userMsg);
        boolean isStandaloneType = STANDALONE_TYPE_PATTERN.matcher(userMsg.trim()).find();
        int orderLimit = isOrderRequest ? extractOrderResultLimit(userMsg) : DEFAULT_ORDER_RESULT_LIMIT;

        String searchKeyword = null;
        String searchTypeCategory = null;

        if (isStandaloneType) {
            searchTypeCategory = typeCategory;
        } else {
            searchKeyword = keyword;
        }

        log.info("AI search command: msg='{}', keyword='{}', typeCategory='{}', isStandalone={}",
                userMsg, searchKeyword, searchTypeCategory, isStandaloneType);

        List<FileInfo> userFiles = fileMapper.selectByUserId(userId);
        List<FileScore> rankedFiles;
        List<FileInfo> matchedFiles;

        if (isOrderRequest) {
            matchedFiles = selectOrderedFiles(userFiles, searchTypeCategory, searchKeyword, userMsg);
            if (matchedFiles.size() > orderLimit) {
                matchedFiles = new ArrayList<>(matchedFiles.subList(0, orderLimit));
            }
            rankedFiles = matchedFiles.stream().map(file -> new FileScore(file, 260)).collect(Collectors.toList());
        } else {
            rankedFiles = rankFilesByQuery(userFiles, userMsg, searchKeyword, searchTypeCategory, 70);
            matchedFiles = rankedFiles.stream().map(item -> item.file).collect(Collectors.toList());
        }

        StringBuilder intro = new StringBuilder("已收到，我来帮你查找。点击消息中的“在文件列表中定位”即可直接跳转并筛选。");
        if (matchedFiles.isEmpty()) {
            intro.append("\n当前未匹配到结果，你可以点击定位后调整筛选条件。");
        } else {
            if (isOrderRequest) {
                intro.append("\n已按“").append(getOrderRequestLabel(userMsg)).append("”筛选，当前命中 ")
                        .append(matchedFiles.size()).append(" 个文件，示例：");
            } else {
                intro.append("\n当前命中 ").append(matchedFiles.size()).append(" 个文件，示例：");
            }
            int previewCount = Math.min(3, matchedFiles.size());
            for (int i = 0; i < previewCount; i++) {
                FileInfo file = matchedFiles.get(i);
                intro.append("\n- ").append(file.getOriginalFilename())
                        .append("（").append(formatFileSize(file.getFileSize()))
                        .append("，上传于 ").append(formatUploadTime(file)).append("）");
            }
        }

        emitter.send(SseEmitter.event().data(intro.toString()));

        Map<String, String> searchParams = new LinkedHashMap<>();
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            searchParams.put("keyword", searchKeyword);
        }
        if (searchTypeCategory != null && !searchTypeCategory.isBlank()) {
            searchParams.put("typeCategory", searchTypeCategory);
        }
        if (fileNameHint != null && !fileNameHint.isBlank()) {
            searchParams.put("fileName", fileNameHint);
        } else if (isOrderRequest && !matchedFiles.isEmpty()) {
            // 比较级请求默认定位到首个候选文件。
            searchParams.put("fileName", matchedFiles.get(0).getOriginalFilename());
        } else if (!rankedFiles.isEmpty() && rankedFiles.get(0).score >= 900) {
            // 强匹配时透传文件名，保证前端定位命中。
            searchParams.put("fileName", rankedFiles.get(0).file.getOriginalFilename());
        }
        searchParams.put("actionLabel", "在文件列表中定位");

        String jsonParams = objectMapper.writeValueAsString(searchParams);
        emitter.send(SseEmitter.event().data(SEARCH_PREFIX + jsonParams));

        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }

    private void handleFileDelete(String userMsg, Long userId, SseEmitter emitter) throws Exception {
        List<FileInfo> userFiles = fileMapper.selectByUserId(userId);
        if (userFiles == null || userFiles.isEmpty()) {
            sendDoneText(emitter, "你当前还没有可删除的文件。");
            return;
        }

        Long fileIdHint = extractFileIdHint(userMsg);
        if (fileIdHint != null) {
            Optional<FileInfo> fileById = userFiles.stream()
                    .filter(file -> Objects.equals(file.getId(), fileIdHint))
                    .findFirst();
            if (fileById.isPresent()) {
                FileInfo target = fileById.get();
                fileService.deleteFile(target.getId(), userId);
                String message = "已将“" + target.getOriginalFilename() + "”移入回收站。"
                        + "\n文件ID：" + target.getId()
                        + "\n大小：" + formatFileSize(target.getFileSize())
                        + "\n类型：" + formatCategory(resolveTypeCategory(target));
                sendDoneText(emitter, message);
                return;
            }
        }

        boolean isOrderRequest = isOrderRequest(userMsg);
        String keyword = isOrderRequest ? extractOrderAwareKeyword(userMsg) : extractDeleteKeyword(userMsg);
        String typeCategory = extractTypeCategory(userMsg);
        List<FileScore> rankedFiles;

        if (isOrderRequest) {
            List<FileInfo> ordered = selectOrderedFiles(userFiles, typeCategory, keyword, userMsg);
            rankedFiles = ordered.isEmpty()
                    ? Collections.emptyList()
                    : List.of(new FileScore(ordered.get(0), 260));
        } else {
            rankedFiles = rankFilesByQuery(userFiles, userMsg, keyword, typeCategory, 90);
        }

        if (rankedFiles.isEmpty()) {
            if (keyword == null || keyword.isBlank()) {
                sendDoneText(emitter, "我还没定位到你要删哪个文件。你可以补充文件名、文件ID，或说“删除最新上传的文档”。");
            } else {
                sendDoneText(emitter, "没有找到匹配“" + keyword + "”的文件。你可以先让我帮你查找，再确认删除。");
            }
            return;
        }

        if (rankedFiles.size() > 1 && !isConfidentTopMatch(rankedFiles)) {
            StringBuilder sb = new StringBuilder("找到多个同名或相近文件，请说得更具体一些：");
            int previewCount = Math.min(5, rankedFiles.size());
            for (int i = 0; i < previewCount; i++) {
                FileInfo file = rankedFiles.get(i).file;
                sb.append("\n- [").append(file.getId()).append("] ").append(file.getOriginalFilename())
                        .append("（").append(formatFileSize(file.getFileSize()))
                        .append("，上传于 ").append(formatUploadTime(file)).append("）");
            }
            if (rankedFiles.size() > previewCount) {
                sb.append("\n还有 ").append(rankedFiles.size() - previewCount).append(" 个候选项。");
            }
            sb.append("\n你也可以直接说：删除文件ID 123。");
            sendDoneText(emitter, sb.toString());
            return;
        }

        FileInfo target = rankedFiles.get(0).file;
        int topScore = rankedFiles.get(0).score;

        if (shouldAskDeleteConfirmation(rankedFiles)) {
            savePendingDelete(userId, target, topScore);
            String confidence = getDeleteConfidenceLabel(topScore);
            String message = "我推断你可能要删除“" + target.getOriginalFilename() + "”，先和你确认一下。"
                    + "\n文件ID：" + target.getId()
                    + "\n大小：" + formatFileSize(target.getFileSize())
                    + "\n类型：" + formatCategory(resolveTypeCategory(target))
                    + "\n置信度：" + confidence
                    + "\n请回复“确认删除”继续，或回复“取消”终止。";
            sendDoneText(emitter, message);
            return;
        }

        fileService.deleteFile(target.getId(), userId);

        String message = "已将“" + target.getOriginalFilename() + "”移入回收站。"
                + "\n文件ID：" + target.getId()
                + "\n大小：" + formatFileSize(target.getFileSize())
                + "\n类型：" + formatCategory(resolveTypeCategory(target));
        sendDoneText(emitter, message);
    }

    private void handleFileInfoQuery(String userMsg, Long userId, SseEmitter emitter) throws Exception {
        List<FileInfo> userFiles = fileMapper.selectByUserId(userId);
        if (userFiles == null || userFiles.isEmpty()) {
            sendDoneText(emitter, "你当前还没有文件可供分析。上传后再问我文件详情，我可以直接回答。\n");
            return;
        }

        boolean isStandaloneType = STANDALONE_TYPE_PATTERN.matcher(userMsg.trim()).find();
        String fileNameHint = extractFileNameHint(userMsg);
        boolean isOrderRequest = isOrderRequest(userMsg);
        String keyword = fileNameHint != null ? fileNameHint
                : (isStandaloneType ? null
                        : (isOrderRequest ? extractOrderAwareKeyword(userMsg) : extractKeyword(userMsg)));
        String typeCategory = extractTypeCategory(userMsg);
        int orderLimit = isOrderRequest ? extractOrderResultLimit(userMsg) : DEFAULT_ORDER_RESULT_LIMIT;

        List<FileInfo> matchedFiles = isOrderRequest
                ? selectOrderedFiles(userFiles, typeCategory, keyword, userMsg)
                : filterFiles(userFiles, keyword, typeCategory, userMsg);
        if (matchedFiles.isEmpty()) {
            sendDoneText(emitter, "没有找到匹配条件的文件。你可以换个文件名关键词或类型再试。\n");
            return;
        }

        long totalSize = matchedFiles.stream().mapToLong(file -> file.getFileSize() != null ? file.getFileSize() : 0L)
                .sum();

        StringBuilder answer = new StringBuilder();
        answer.append("匹配到 ").append(matchedFiles.size()).append(" 个文件");
        if (keyword != null && !keyword.isBlank()) {
            answer.append("（关键词：").append(keyword).append("）");
        }
        if (typeCategory != null && !typeCategory.isBlank()) {
            answer.append("（类型：").append(formatCategory(typeCategory)).append("）");
        }
        answer.append("。\n");
        answer.append("总大小约 ").append(formatFileSize(totalSize)).append("。\n");

        if (RECENT_REQUEST_PATTERN.matcher(userMsg).find()) {
            answer.append("\n最近上传：");
            matchedFiles.stream()
                    .sorted(Comparator.comparing(FileInfo::getUploadTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(orderLimit)
                    .forEach(file -> answer.append("\n- ")
                            .append(file.getOriginalFilename())
                            .append("（").append(formatUploadTime(file)).append("）"));
        }

        if (OLDEST_REQUEST_PATTERN.matcher(userMsg).find()) {
            answer.append("\n\n最早上传的文件：");
            matchedFiles.stream()
                    .sorted(Comparator.comparing(FileInfo::getUploadTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(orderLimit)
                    .forEach(file -> answer.append("\n- ")
                            .append(file.getOriginalFilename())
                            .append("（").append(formatUploadTime(file)).append("）"));
        }

        if (LARGEST_REQUEST_PATTERN.matcher(userMsg).find()) {
            answer.append("\n\n占用空间最大的文件：");
            matchedFiles.stream()
                    .sorted(Comparator.comparing(file -> file.getFileSize() != null ? file.getFileSize() : 0L,
                            Comparator.reverseOrder()))
                    .limit(orderLimit)
                    .forEach(file -> answer.append("\n- ")
                            .append(file.getOriginalFilename())
                            .append("（").append(formatFileSize(file.getFileSize())).append("）"));
        }

        if (SMALLEST_REQUEST_PATTERN.matcher(userMsg).find()) {
            answer.append("\n\n占用空间最小的文件：");
            matchedFiles.stream()
                    .sorted(Comparator.comparing(file -> file.getFileSize() != null ? file.getFileSize() : 0L))
                    .limit(orderLimit)
                    .forEach(file -> answer.append("\n- ")
                            .append(file.getOriginalFilename())
                            .append("（").append(formatFileSize(file.getFileSize())).append("）"));
        }

        boolean needDetailList = DETAIL_REQUEST_PATTERN.matcher(userMsg).find() || matchedFiles.size() <= 3;
        if (needDetailList) {
            answer.append("\n\n文件详情：");
            int detailCount = Math.min(MAX_DETAIL_ROWS, matchedFiles.size());
            for (int i = 0; i < detailCount; i++) {
                FileInfo file = matchedFiles.get(i);
                answer.append("\n- [").append(file.getId()).append("] ")
                        .append(file.getOriginalFilename())
                        .append(" | ").append(formatCategory(resolveTypeCategory(file)))
                        .append(" | ").append(formatFileSize(file.getFileSize()))
                        .append(" | 上传 ").append(formatUploadTime(file))
                        .append(" | 下载 ").append(file.getDownloadCount() != null ? file.getDownloadCount() : 0)
                        .append(" 次");
                if (file.getDescription() != null && !file.getDescription().isBlank()) {
                    answer.append(" | 描述：").append(file.getDescription());
                }
            }
            if (matchedFiles.size() > detailCount) {
                answer.append("\n\n其余 ").append(matchedFiles.size() - detailCount).append(" 个文件可继续按名称询问详情。");
            }
        } else {
            answer.append("\n如需具体文件详情，可继续问：某个文件的大小、类型、上传时间或下载次数。\n");
        }

        sendDoneText(emitter, answer.toString().trim());
    }

    private List<FileInfo> filterFiles(List<FileInfo> files, String keyword, String typeCategory, String userMsg) {
        List<FileScore> ranked = rankFilesByQuery(files, userMsg, keyword, typeCategory, 65);
        return ranked.stream().map(item -> item.file).collect(Collectors.toList());
    }

    private boolean isConfidentTopMatch(List<FileScore> rankedFiles) {
        if (rankedFiles == null || rankedFiles.isEmpty()) {
            return false;
        }
        if (rankedFiles.size() == 1) {
            return true;
        }
        int top = rankedFiles.get(0).score;
        int second = rankedFiles.get(1).score;
        return top >= 900 || (top >= 320 && (top - second) >= 120) || (top >= 250 && second < 100);
    }

    private boolean shouldAskDeleteConfirmation(List<FileScore> rankedFiles) {
        if (rankedFiles == null || rankedFiles.isEmpty()) {
            return false;
        }

        int top = rankedFiles.get(0).score;
        if (top >= 900) {
            return false;
        }

        if (rankedFiles.size() == 1) {
            return top < 520;
        }

        int second = rankedFiles.get(1).score;
        int gap = top - second;
        return top < 620 || gap < 200;
    }

    private void savePendingDelete(Long userId, FileInfo file, int score) {
        if (userId == null || file == null || file.getId() == null) {
            return;
        }
        pendingDeleteMap.put(userId,
                new PendingDeleteContext(file.getId(), file.getOriginalFilename(), score, System.currentTimeMillis()));
    }

    private PendingDeleteContext getValidPendingDelete(Long userId) {
        if (userId == null) {
            return null;
        }

        PendingDeleteContext pending = pendingDeleteMap.get(userId);
        if (pending == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - pending.createdAt > DELETE_CONFIRM_TTL_MILLIS) {
            pendingDeleteMap.remove(userId);
            return null;
        }
        return pending;
    }

    private String getDeleteConfidenceLabel(int score) {
        if (score >= 700) {
            return "较高";
        }
        if (score >= 420) {
            return "中等";
        }
        return "偏低";
    }

    private boolean tryHandlePendingDeleteConfirmation(String userMsg, Long userId, SseEmitter emitter)
            throws Exception {
        PendingDeleteContext pending = getValidPendingDelete(userId);
        if (pending == null) {
            return false;
        }

        String msg = userMsg == null ? "" : userMsg.trim();
        if (msg.isBlank()) {
            return false;
        }

        if (DELETE_CANCEL_PATTERN.matcher(msg).find()) {
            pendingDeleteMap.remove(userId);
            sendDoneText(emitter, "已取消本次删除操作。文件不会被移入回收站。");
            return true;
        }

        if (!DELETE_CONFIRM_PATTERN.matcher(msg).find()) {
            if (isDeleteIntent(msg)) {
                // 用户发起了新的删除请求，清除旧的待确认上下文，继续走新请求解析。
                pendingDeleteMap.remove(userId);
            }
            return false;
        }

        pendingDeleteMap.remove(userId);

        FileInfo target = fileMapper.selectByIdAndUserId(pending.fileId, userId);
        if (target == null) {
            sendDoneText(emitter, "待确认删除的文件不存在或无访问权限，请重新发起删除请求。");
            return true;
        }

        fileService.deleteFile(target.getId(), userId);
        String message = "已确认并将“" + target.getOriginalFilename() + "”移入回收站。"
                + "\n文件ID：" + target.getId()
                + "\n大小：" + formatFileSize(target.getFileSize())
                + "\n类型：" + formatCategory(resolveTypeCategory(target));
        sendDoneText(emitter, message);
        return true;
    }

    private String resolveTypeCategory(FileInfo file) {
        if (file == null) {
            return "other";
        }
        if (Integer.valueOf(1).equals(file.getIsFolder())) {
            return "folder";
        }

        String mime = Optional.ofNullable(file.getFileType()).orElse("").toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/"))
            return "image";
        if (mime.startsWith("video/"))
            return "video";
        if (mime.startsWith("audio/"))
            return "audio";
        if (mime.startsWith("text/"))
            return "text";

        String ext = getFileExt(file.getOriginalFilename());
        if (IMAGE_EXTS.contains(ext))
            return "image";
        if (VIDEO_EXTS.contains(ext))
            return "video";
        if (AUDIO_EXTS.contains(ext))
            return "audio";
        if (CODE_EXTS.contains(ext))
            return "code";
        if (TEXT_EXTS.contains(ext))
            return "text";
        if (ARCHIVE_EXTS.contains(ext))
            return "archive";
        if (DOCUMENT_EXTS.contains(ext))
            return "document";

        if (mime.contains("pdf")
                || mime.contains("msword")
                || mime.contains("presentation")
                || mime.contains("spreadsheet")
                || mime.contains("excel")) {
            return "document";
        }
        if (mime.contains("zip")
                || mime.contains("rar")
                || mime.contains("7z")
                || mime.contains("tar")
                || mime.contains("gzip")) {
            return "archive";
        }

        return "other";
    }

    private String getFileExt(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String formatCategory(String typeCategory) {
        if (typeCategory == null || typeCategory.isBlank()) {
            return "其他";
        }
        Map<String, String> labels = Map.of(
                "folder", "目录",
                "image", "图片",
                "video", "视频",
                "audio", "音频",
                "text", "文本",
                "code", "代码",
                "archive", "压缩包",
                "document", "文档",
                "other", "其他");
        return labels.getOrDefault(typeCategory, typeCategory);
    }

    private String formatUploadTime(FileInfo file) {
        if (file == null || file.getUploadTime() == null) {
            return "-";
        }
        return file.getUploadTime().format(TIME_FORMATTER);
    }

    private String formatFileSize(Long bytes) {
        long size = bytes != null ? bytes : 0L;
        if (size <= 0) {
            return "0 B";
        }
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        double value = size;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }

    private void sendDoneText(SseEmitter emitter, String text) throws IOException {
        emitter.send(SseEmitter.event().data(text));
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

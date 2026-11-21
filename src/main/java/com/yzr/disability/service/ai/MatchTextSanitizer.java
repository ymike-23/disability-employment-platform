package com.yzr.disability.service.ai;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 仅用于匹配资料的保守过滤；不修改原始业务数据。 */
public final class MatchTextSanitizer {
    private static final Pattern SENSITIVE = Pattern.compile(
            "姓名|我叫|本人|联系人|负责人|身份证|证件|证号|出生|生日|年龄|性别|男士|女士|先生|小姐|"
            + "住址|地址|户籍|网格|电话|手机|联系方式|微信|邮箱|残疾|残障|听障|视障|肢障|智障|"
            + "肢体|视力|听力|言语|智力|精神|病史|诊断|疾病|病情|轮椅|康复|医疗|健康|治疗|经度|纬度|经纬度|"
            + "id.?card|passport|date.?of.?birth|birth.?date|disability|grid.?id|gender|"
            + "(?:name|contact|address|phone|email|password|token|api.?key)\\s*[:：=]|"
            + "https?://|www\\.|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|sk-[a-z0-9_-]{8,}|"
            + "\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}|"
            + "[\\p{IsHan}]{2,}(?:省|自治区|市|区|县|街道|镇|乡|路|街|巷|弄|村)|"
            + "\\d+(?:号楼|栋|单元|室)|(?-i:[A-Z][a-z]+\\s+[A-Z][a-z]+)|"
            + "ignore\\s+(?:all|previous)|system\\s*prompt|忽略.{0,12}指令|<\\|[^>]+\\|>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{10,}[xX]?");
    private static final Pattern SEPARATED_NUMBER = Pattern.compile(
            "(?<!\\d)(?:1[3-9]\\d{9}|0\\d{9,11}|\\d{15,18}[xX]?)(?!\\d)");
    private final List<String> privateValues;

    public MatchTextSanitizer(Collection<String> privateValues) {
        this.privateValues = privateValues.stream().filter(Objects::nonNull)
                .map(MatchTextSanitizer::normalize).filter(value -> !value.isBlank())
                .map(MatchTextSanitizer::compact).filter(value -> !value.isBlank()).toList();
    }

    /** 含身份、联系、健康等信息或无法确认安全的字段整项省略，不截断后继续外发。 */
    public String clean(String raw, int maxLength) {
        if (raw == null || raw.length() > 8000) return null;
        String value = normalize(raw);
        if (value.isBlank() || value.length() > maxLength || SENSITIVE.matcher(value).find()) return null;
        String lower = compact(value);
        if (privateValues.stream().anyMatch(lower::contains)) return null;
        if (SENSITIVE.matcher(lower).find() || LONG_NUMBER.matcher(value).find()
                || SEPARATED_NUMBER.matcher(lower).find()) return null;
        return value;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cf}\\p{Cc}]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String compact(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", "");
    }
}

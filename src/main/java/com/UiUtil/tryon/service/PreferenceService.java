package com.UiUtil.tryon.service;

/**
 * 用户偏好学习服务：聚合历史反馈标签，更新 user_preference 中的偏好向量（JSON 词频格式），
 * 并将高权重标签转化为负面提示词供下次生图使用。
 */
import com.UiUtil.tryon.entity.UserPreference;
import com.UiUtil.tryon.mapper.UserPreferenceMapper;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PreferenceService {

    @Autowired
    UserPreferenceMapper preferenceMapper;

    private static final Map<String, String> TAG_GUIDANCE = new LinkedHashMap<>();
    static {
        TAG_GUIDANCE.put("model_fake",        "模特看起来像AI合成，请确保皮肤纹理自然真实，避免过于完美的假脸");
        TAG_GUIDANCE.put("pose_stiff",        "姿势太僵硬，请生成自然放松的站姿或动态感姿势");
        TAG_GUIDANCE.put("body_distorted",    "人物比例失真，请保持真实自然的身材比例，避免拉伸变形");
        TAG_GUIDANCE.put("face_blur",         "面部模糊不自然，请提升面部清晰度和真实感");
        TAG_GUIDANCE.put("cloth_unflattering","衣服不显身材，请展示衣物对身形的修饰效果");
        TAG_GUIDANCE.put("color_off",         "颜色色调偏差，请保持衣物色彩的真实准确性");
        TAG_GUIDANCE.put("texture_blur",      "材质纹理模糊，请清晰呈现织物的真实材质感");
        TAG_GUIDANCE.put("detail_lost",       "衣物细节（纽扣/图案/刺绣）丢失，请保留所有服装细节");
        TAG_GUIDANCE.put("scene_dislike",     "背景场景不喜欢，请使用简洁自然的背景");
        TAG_GUIDANCE.put("lighting_bad",      "光线不自然，请使用柔和均匀的自然光效果");
        TAG_GUIDANCE.put("bg_distracting",    "背景太抢眼，请降低背景复杂度，突出服装主体");
        TAG_GUIDANCE.put("style_mismatch",    "整体风格不对，请严格按照所选风格和场景生成");
        TAG_GUIDANCE.put("ref_mismatch",      "与参考图差距太大，请更贴近参考穿搭的构图和风格");
    }

    public String buildPrefPromptPrefix(Long userId) {
        UserPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));
        if (pref == null || pref.getTagWeights() == null) return "";

        Map<String, Integer> weights = JSON.parseObject(
                pref.getTagWeights(), new TypeReference<Map<String, Integer>>() {});
        if (weights == null || weights.isEmpty()) return "";

        List<Map.Entry<String, Integer>> top3 = weights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder("【用户偏好优化】该用户历史反馈需重点改善：\n");
        for (Map.Entry<String, Integer> e : top3) {
            String guidance = TAG_GUIDANCE.getOrDefault(e.getKey(), e.getKey());
            sb.append(" - ").append(guidance)
              .append("（反馈").append(e.getValue()).append("次）;\n");
        }
        sb.append("以上为优先改善方向，其余要求如下：\n");
        return sb.toString();
    }

    public void updateWeights(Long userId, List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) return;

        UserPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));

        Map<String, Integer> weights = new HashMap<>();
        if (pref != null && pref.getTagWeights() != null) {
            weights = new HashMap<>(JSON.parseObject(pref.getTagWeights(),
                    new TypeReference<Map<String, Integer>>() {}));
        }

        for (String tag : tagCodes) {
            weights.merge(tag, 1, Integer::sum);
        }

        String newWeightsJson = JSON.toJSONString(weights);
        if (pref == null) {
            UserPreference newPref = new UserPreference();
            newPref.setUserId(userId);
            newPref.setTagWeights(newWeightsJson);
            preferenceMapper.insert(newPref);
        } else {
            pref.setTagWeights(newWeightsJson);
            preferenceMapper.updateById(pref);
        }
    }
}

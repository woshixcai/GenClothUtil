package com.UiUtil.tryon.service;

/**
 * 用户反馈服务：保存用户对换装结果的不满意原因标签，并触发用户偏好向量的异步更新。
 */
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.tryon.entity.TryonFeedback;
import com.UiUtil.tryon.mapper.TryonFeedbackMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FeedbackService {

    @Autowired TryonFeedbackMapper feedbackMapper;
    @Autowired PreferenceService preferenceService;

    public void submitFeedback(Long recordId, List<String> tagCodes, String extraText) {
        UserContext.LoginUser user = UserContext.current();

        TryonFeedback fb = new TryonFeedback();
        fb.setRecordId(recordId);
        fb.setUserId(user.getUserId());
        fb.setTagCodes(tagCodes == null ? "" : String.join(",", tagCodes));
        fb.setExtraText(extraText);
        fb.setCreatedTime(new Date());
        feedbackMapper.insert(fb);

        preferenceService.updateWeights(user.getUserId(), tagCodes);
    }
}

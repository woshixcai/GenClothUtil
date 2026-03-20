package com.UiUtil.tryon.controller;

import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import com.UiUtil.tryon.service.FeedbackService;
import com.UiUtil.tryon.service.TryonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/tryon")
public class TryonController {

    @Autowired TryonService tryonService;
    @Autowired FeedbackService feedbackService;

    @RequirePermission("image:recommend")
    @PostMapping("/recommend")
    public ApiResult<Map<String, Object>> recommend(
            @RequestParam(required = false) List<MultipartFile> clothesFiles,
            @RequestParam(required = false) List<MultipartFile> referenceFiles,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String season) {
        return ApiResult.ok(tryonService.recommend(clothesFiles, referenceFiles, style, scene, season));
    }

    @RequirePermission("tryon:feedback")
    @PostMapping("/feedback")
    public ApiResult<Void> feedback(@RequestParam Long recordId,
                                     @RequestParam(required = false) List<String> tagCodes,
                                     @RequestParam(required = false) String extraText) {
        feedbackService.submitFeedback(recordId, tagCodes, extraText);
        return ApiResult.ok();
    }
}

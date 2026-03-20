package com.medical.ai.controller;

import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.service.SummaryService;
import com.medical.common.core.domain.R;
import com.medical.common.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    @GetMapping("/session/{sessionId}")
    public R<ConversationSummaryVO> getSummaryBySession(@PathVariable Long sessionId) {
        Long userId = SecurityUtil.getUserId();
        return R.ok(summaryService.getSummaryBySession(sessionId, userId));
    }

    @GetMapping("/appointment/{appointmentId}")
    public R<ConversationSummaryVO> getSummaryByAppointment(@PathVariable Long appointmentId) {
        return R.ok(summaryService.getSummaryByAppointment(appointmentId));
    }
}

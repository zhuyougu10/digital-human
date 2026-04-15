package com.medical.ai.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.api.doctor.RemoteDoctorService;
import com.medical.common.core.domain.R;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriageAgentTest {

    @Mock
    private RemoteDoctorService remoteDoctorService;

    @Test
    void getSystemPrompt_shouldUseLiveDepartmentNamesAndCacheThem() {
        when(remoteDoctorService.getDepartmentNames()).thenReturn(R.ok(List.of("呼吸科", "感染科")));
        TriageAgent triageAgent = new TriageAgent(remoteDoctorService);

        String firstPrompt = triageAgent.getSystemPrompt();
        String secondPrompt = triageAgent.getSystemPrompt();

        assertTrue(firstPrompt.contains("呼吸科、感染科"));
        assertTrue(firstPrompt.contains("工具参数必须同时传 doctorId、doctorName、date"));
        assertTrue(firstPrompt.contains("createAppointment 时只能使用 getAvailableSlots 返回的真实 slotId"));
        assertTrue(firstPrompt.contains("系统当前显示挂号费为 0 元"));
        assertTrue(secondPrompt.contains("呼吸科、感染科"));
        verify(remoteDoctorService, times(1)).getDepartmentNames();
    }

    @Test
    void getSystemPrompt_shouldFallBackWhenRemoteCallFails() {
        when(remoteDoctorService.getDepartmentNames()).thenReturn(R.fail("unavailable"));
        TriageAgent triageAgent = new TriageAgent(remoteDoctorService);

        String prompt = triageAgent.getSystemPrompt();

        assertTrue(prompt.contains("内科、外科、神经内科"));
    }
}

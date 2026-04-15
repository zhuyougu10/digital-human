package com.medical.ai.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorSearchToolTest {

    @Mock
    private RemoteDoctorService remoteDoctorService;

    @Mock
    private RemoteScheduleService remoteScheduleService;

    private DoctorSearchTool doctorSearchTool;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        doctorSearchTool = new DoctorSearchTool(remoteDoctorService, remoteScheduleService, directExecutor);
    }

    @Test
    void getAvailableSlots_shouldReturnSlotsWhenDoctorMatches() {
        GetSlotsRequest request = new GetSlotsRequest();
        request.setDoctorId(1L);
        request.setDoctorName("张三");
        request.setDate("2026-04-16");

        DoctorInfoDTO doctor = new DoctorInfoDTO();
        doctor.setId(1L);
        doctor.setName("张三");

        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(419L);
        slot.setDoctorId(1L);
        slot.setDoctorName("张三");

        when(remoteDoctorService.getDoctorById(1L)).thenReturn(R.ok(doctor));
        when(remoteScheduleService.getAvailableSlots(1L, "2026-04-16")).thenReturn(R.ok(List.of(slot)));

        List<SlotInfoDTO> result = doctorSearchTool.getAvailableSlots().apply(request);

        assertEquals(1, result.size());
        assertEquals(419L, result.get(0).getId());
    }

    @Test
    void getAvailableSlots_shouldCorrectMismatchedDoctorSelectionByName() {
        GetSlotsRequest request = new GetSlotsRequest();
        request.setDoctorId(3L);
        request.setDoctorName("张三");
        request.setDate("2026-04-16");

        DoctorInfoDTO doctorById = new DoctorInfoDTO();
        doctorById.setId(3L);
        doctorById.setName("王磊");

        DoctorInfoDTO doctorByName = new DoctorInfoDTO();
        doctorByName.setId(1L);
        doctorByName.setName("张三");

        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(520L);
        slot.setDoctorId(1L);
        slot.setDoctorName("张三");

        when(remoteDoctorService.getDoctorById(3L)).thenReturn(R.ok(doctorById));
        when(remoteDoctorService.getDoctorByName("张三")).thenReturn(R.ok(doctorByName));
        when(remoteScheduleService.getAvailableSlots(1L, "2026-04-16")).thenReturn(R.ok(List.of(slot)));

        List<SlotInfoDTO> result = doctorSearchTool.getAvailableSlots().apply(request);

        assertEquals(1, result.size());
        assertEquals(520L, result.get(0).getId());
        verify(remoteScheduleService, never()).getAvailableSlots(3L, "2026-04-16");
    }

    @Test
    void getAvailableSlots_shouldRejectMismatchedReturnedSlots() {
        GetSlotsRequest request = new GetSlotsRequest();
        request.setDoctorId(1L);
        request.setDoctorName("张三");
        request.setDate("2026-04-16");

        DoctorInfoDTO doctor = new DoctorInfoDTO();
        doctor.setId(1L);
        doctor.setName("张三");

        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(415L);
        slot.setDoctorId(3L);
        slot.setDoctorName("王磊");

        when(remoteDoctorService.getDoctorById(1L)).thenReturn(R.ok(doctor));
        when(remoteScheduleService.getAvailableSlots(1L, "2026-04-16")).thenReturn(R.ok(List.of(slot)));

        CompletionException error = assertThrows(CompletionException.class,
                () -> doctorSearchTool.getAvailableSlots().apply(request));

        assertEquals("号源返回的医生信息与所选医生不一致，请重新查询", error.getCause().getMessage());
    }
}

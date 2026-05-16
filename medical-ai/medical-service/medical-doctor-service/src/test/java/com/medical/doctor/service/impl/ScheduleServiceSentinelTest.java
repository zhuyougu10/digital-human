package com.medical.doctor.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.redis.util.RedisUtil;
import com.medical.doctor.mapper.DoctorDepartmentMapper;
import com.medical.doctor.mapper.DoctorProfileMapper;
import com.medical.doctor.mapper.ScheduleSlotMapper;
import com.medical.doctor.mapper.ScheduleTemplateMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleServiceSentinelTest {

    private ScheduleServiceImpl scheduleService;

    @BeforeEach
    void setUp() {
        ParamFlowRuleManager.loadRules(List.of());
        scheduleService = new ScheduleServiceImpl(
                mock(ScheduleTemplateMapper.class),
                mock(ScheduleSlotMapper.class),
                mock(DoctorProfileMapper.class),
                mock(DoctorDepartmentMapper.class),
                mock(RedisUtil.class));
    }

    @AfterEach
    void tearDown() {
        ParamFlowRuleManager.loadRules(List.of());
    }

    @Test
    void getAvailableSlots_shouldFastFailWhenHotspotRuleTriggers() throws Throwable {
        ParamFlowRule rule = new ParamFlowRule(ScheduleServiceImpl.SLOTS_RESOURCE);
        rule.setParamIdx(0);
        rule.setCount(1);
        ParamFlowRuleManager.loadRules(List.of(rule));

        String hotspot = "1:2026-03-24";
        SphU.entry(ScheduleServiceImpl.SLOTS_RESOURCE, EntryType.IN, 1, hotspot).exit();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scheduleService.getAvailableSlots(1L, LocalDate.of(2026, 3, 24)));

        assertEquals(ErrorCode.FAIL.getCode(), ex.getCode());
        assertEquals(ScheduleServiceImpl.SLOTS_BUSY_MESSAGE, ex.getMessage());
    }
}

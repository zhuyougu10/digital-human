package com.medical.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.appointment.domain.entity.AppointmentAuditRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppointmentAuditRecordMapper extends BaseMapper<AppointmentAuditRecord> {
}

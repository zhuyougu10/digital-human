package com.medical.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.appointment.domain.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}

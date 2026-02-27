package com.medical.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.doctor.domain.entity.DoctorProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DoctorProfileMapper extends BaseMapper<DoctorProfile> {
}

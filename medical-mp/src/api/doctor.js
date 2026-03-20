import request from './request'

export const getDoctorList = (params) => {
  return request({
    url: '/doctor/doctor/list',
    method: 'GET',
    data: params
  })
}

export const getDoctorById = (id) => {
  return request({
    url: `/doctor/doctor/${id}`,
    method: 'GET'
  })
}

export const getDepartmentList = () => {
  return request({
    url: '/doctor/department/list',
    method: 'GET'
  })
}

export const getAvailableSlots = (params) => {
  return request({
    url: '/doctor/schedule/slots',
    method: 'GET',
    data: params
  })
}

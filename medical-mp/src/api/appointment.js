import request from './request'

export const createAppointment = (data) => {
  return request({
    url: '/appointment/appointment',
    method: 'POST',
    data
  })
}

export const getMyAppointments = (params) => {
  return request({
    url: '/appointment/appointment/my',
    method: 'GET',
    data: params
  })
}

export const getAppointmentById = (id) => {
  return request({
    url: `/appointment/appointment/${id}`,
    method: 'GET'
  })
}

export const cancelAppointment = (id) => {
  return request({
    url: `/appointment/appointment/${id}/cancel`,
    method: 'PUT'
  })
}

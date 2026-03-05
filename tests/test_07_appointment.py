from datetime import date, timedelta

from conftest import assert_success, auth_header, ensure_doctor_token, ensure_patient_token, extract_records


def _ensure_slot(api, state):
    if state.available_slot_id:
        return state.available_slot_id
    target = (date.today() + timedelta(days=1)).isoformat()
    state.available_slot_date = target
    response = api.request(
        "GET",
        f"/doctor/schedule/slots?doctorId={state.doctor_profile_id}&date={target}",
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    assert data, "No available slots found for appointment tests"
    state.available_slot_id = data[0].get("id")
    return state.available_slot_id


def test_create_appointment(api, state):
    patient_token = ensure_patient_token(api, state)
    slot_id = _ensure_slot(api, state)
    response = api.request(
        "POST",
        "/appointment/appointment",
        headers=auth_header(patient_token),
        json={
            "doctorId": state.doctor_profile_id,
            "departmentId": state.department_id or 1,
            "slotId": slot_id,
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    state.appointment_id = payload.get("data")
    assert state.appointment_id is not None


def test_my_appointments(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "GET",
        "/appointment/appointment/my?pageNum=1&pageSize=10",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert isinstance(extract_records(payload), list)


def test_appointment_detail(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "GET",
        f"/appointment/appointment/{state.appointment_id}",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert payload["data"]["doctorId"] == state.doctor_profile_id


def test_doctor_appointments(api, state):
    doctor_token = ensure_doctor_token(api, state)
    response = api.request(
        "GET",
        "/appointment/appointment/doctor",
        headers=auth_header(doctor_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_admin_list(api, state):
    response = api.request(
        "GET",
        "/appointment/appointment/list?pageNum=1&pageSize=10",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert isinstance(extract_records(payload), list)


def test_admin_statistics(api, state):
    response = api.request(
        "GET",
        "/appointment/appointment/statistics",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert isinstance(payload.get("data"), dict)


def test_cancel_appointment(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "PUT",
        f"/appointment/appointment/{state.appointment_id}/cancel",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_verify_cancelled(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "GET",
        f"/appointment/appointment/{state.appointment_id}",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert payload["data"]["status"] == 2

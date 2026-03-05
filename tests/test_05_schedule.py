from datetime import date, timedelta

from conftest import assert_success, auth_header


def _tomorrow() -> date:
    return date.today() + timedelta(days=1)


def test_create_template(api, state):
    target = _tomorrow()
    response = api.request(
        "POST",
        f"/doctor/schedule/template/{state.doctor_profile_id}",
        json={
            "dayOfWeek": target.isoweekday(),
            "period": "morning",
            "startTime": "09:00:00",
            "endTime": "09:30:00",
            "maxPatients": 5,
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_get_templates(api, state):
    response = api.request("GET", f"/doctor/schedule/template/{state.doctor_profile_id}")
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    assert isinstance(data, list)
    if data:
        state.schedule_template_id = data[0].get("id")


def test_generate_slots(api, state):
    target = _tomorrow().isoformat()
    response = api.request(
        "POST",
        f"/doctor/schedule/generate?startDate={target}&endDate={target}",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_get_slots_by_doctor(api, state):
    target = _tomorrow().isoformat()
    state.available_slot_date = target
    response = api.request(
        "GET",
        f"/doctor/schedule/slots?doctorId={state.doctor_profile_id}&date={target}",
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    assert isinstance(data, list)
    if data:
        state.available_slot_id = data[0].get("id")


def test_get_slots_by_department(api, state):
    target = _tomorrow().isoformat()
    response = api.request(
        "GET",
        f"/doctor/schedule/slots/department?departmentId={state.department_id}&date={target}",
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_delete_template(api, state):
    response = api.request("DELETE", f"/doctor/schedule/template/{state.schedule_template_id}")
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_template_deleted(api, state):
    response = api.request("GET", f"/doctor/schedule/template/{state.doctor_profile_id}")
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    assert all(item.get("id") != state.schedule_template_id for item in data)

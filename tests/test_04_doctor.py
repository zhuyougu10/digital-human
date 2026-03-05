from conftest import assert_success, auth_header, ensure_doctor_token, extract_records


def _ensure_available_department_id(api, state):
    if state.department_id:
        check = api.request("GET", f"/doctor/department/{state.department_id}")
        check_payload = api.require_http_ok(check)
        if check_payload.get("code") == 200:
            return state.department_id
        state.department_id = None
    response = api.request("GET", "/doctor/department/list")
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    assert data, "No department available for doctor profile tests"
    state.department_id = data[0].get("id")
    return state.department_id


def test_create_doctor(api, state):
    department_id = _ensure_available_department_id(api, state)
    doctor_name = f"IT Doctor {state.run_suffix}"
    response = api.request(
        "POST",
        "/doctor/doctor",
        headers=auth_header(state.admin_token),
        json={
            "userId": state.doctor_user_id,
            "name": doctor_name,
            "title": "Chief Physician",
            "introduction": "Integration test doctor profile",
            "specialties": "头痛,发热",
            "treatmentAreas": "内科",
            "consultationFee": 25.5,
            "departmentIds": [department_id],
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)

    listing = api.request("GET", "/doctor/doctor/list?pageNum=1&pageSize=50")
    listing_payload = api.require_http_ok(listing)
    assert_success(listing_payload)
    matched = next((x for x in extract_records(listing_payload) if x.get("name") == doctor_name), None)
    assert matched is not None
    state.doctor_profile_id = matched.get("id")


def test_list_doctors(api):
    response = api.request("GET", "/doctor/doctor/list?pageNum=1&pageSize=10")
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert isinstance(extract_records(payload), list)


def test_list_by_department(api, state):
    department_id = _ensure_available_department_id(api, state)
    response = api.request(
        "GET",
        f"/doctor/doctor/list?pageNum=1&pageSize=10&departmentId={department_id}",
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_get_doctor(api, state):
    response = api.request("GET", f"/doctor/doctor/{state.doctor_profile_id}")
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert payload["data"]["id"] == state.doctor_profile_id


def test_admin_update_doctor(api, state):
    response = api.request(
        "PUT",
        f"/doctor/doctor/{state.doctor_profile_id}",
        headers=auth_header(state.admin_token),
        json={
            "title": "Attending Physician",
            "introduction": "Updated by integration test",
            "consultationFee": 28.0,
            "departmentIds": [_ensure_available_department_id(api, state)],
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_doctor_view_profile(api, state):
    token = ensure_doctor_token(api, state)
    response = api.request("GET", "/doctor/doctor/my-profile", headers=auth_header(token))
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_doctor_update_profile(api, state):
    token = ensure_doctor_token(api, state)
    response = api.request(
        "PUT",
        "/doctor/doctor/my-profile",
        headers=auth_header(token),
        json={
            "introduction": "Doctor self-update profile via integration tests",
            "specialties": "头痛,咳嗽",
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_search_by_symptom(api):
    response = api.request("GET", "/doctor/doctor/search?keywords=头痛")
    payload = api.require_http_ok(response)
    assert_success(payload)

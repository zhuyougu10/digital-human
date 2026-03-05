from conftest import (
    assert_success,
    auth_header,
    ensure_doctor_token,
    extract_records,
    find_user_id_by_username,
)


def test_user_list_paginated(api, state):
    response = api.request(
        "GET",
        "/user/user/list?pageNum=1&pageSize=10",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert isinstance(extract_records(payload), list)


def test_user_list_search(api, state):
    response = api.request(
        "GET",
        "/user/user/list?pageNum=1&pageSize=10&keyword=admin",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    usernames = [item.get("username", "") for item in extract_records(payload)]
    assert any("admin" in name for name in usernames)


def test_assign_doctor_role(api, state):
    doctor_user_id = find_user_id_by_username(api, state.admin_token, state.doctor_username)
    assert doctor_user_id is not None
    state.doctor_user_id = doctor_user_id

    response = api.request(
        "POST",
        f"/user/user/{doctor_user_id}/role/DOCTOR",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_disable_user(api, state):
    response = api.request(
        "PUT",
        f"/user/user/{state.patient_user_id}/toggle-status",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_enable_user(api, state):
    response = api.request(
        "PUT",
        f"/user/user/{state.patient_user_id}/toggle-status",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_remove_role(api, state):
    response = api.request(
        "DELETE",
        f"/user/user/{state.doctor_user_id}/role/DOCTOR",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)

    restore = api.request(
        "POST",
        f"/user/user/{state.doctor_user_id}/role/DOCTOR",
        headers=auth_header(state.admin_token),
    )
    restore_payload = api.require_http_ok(restore)
    assert_success(restore_payload)
    state.doctor_token = ensure_doctor_token(api, state)

from conftest import (
    assert_failure,
    assert_success,
    auth_header,
    ensure_patient_token,
    login_with_password,
)
from config import ADMIN_PASSWORD, ADMIN_USERNAME, TEST_PASSWORD


def test_admin_login(api, state):
    token, user = login_with_password(api, ADMIN_USERNAME, ADMIN_PASSWORD)
    assert token
    assert user.get("username") == ADMIN_USERNAME
    state.admin_token = token
    state.admin_user_id = user.get("id")


def test_login_wrong_password(api):
    response = api.request(
        "POST",
        "/user/auth/login",
        json={"username": ADMIN_USERNAME, "password": "wrong-password"},
    )
    payload = api.require_http_ok(response)
    assert_failure(payload)


def test_register_patient(api, state):
    state.patient_username = f"test_patient_{state.run_suffix}"
    state.doctor_username = f"test_doctor_{state.run_suffix}"

    patient_resp = api.request(
        "POST",
        "/user/auth/register",
        json={
            "username": state.patient_username,
            "password": TEST_PASSWORD,
            "nickname": "patient-integration",
            "phone": "13800000001",
        },
    )
    patient_payload = api.require_http_ok(patient_resp)
    assert_success(patient_payload)

    doctor_resp = api.request(
        "POST",
        "/user/auth/register",
        json={
            "username": state.doctor_username,
            "password": TEST_PASSWORD,
            "nickname": "doctor-integration",
            "phone": "13800000002",
        },
    )
    doctor_payload = api.require_http_ok(doctor_resp)
    assert_success(doctor_payload)


def test_register_duplicate(api, state):
    response = api.request(
        "POST",
        "/user/auth/register",
        json={
            "username": state.patient_username,
            "password": TEST_PASSWORD,
            "nickname": "dup-user",
        },
    )
    payload = api.require_http_ok(response)
    assert_failure(payload)


def test_patient_login(api, state):
    token, user = login_with_password(api, state.patient_username, TEST_PASSWORD)
    assert token
    state.patient_token = token
    state.patient_user_id = user.get("id")
    assert user.get("username") == state.patient_username


def test_get_current_user_info(api, state):
    token = ensure_patient_token(api, state)
    response = api.request("GET", "/user/user/info", headers=auth_header(token))
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert payload["data"]["username"] == state.patient_username


def test_logout(api, state):
    token = ensure_patient_token(api, state)
    response = api.request("POST", "/user/auth/logout", headers=auth_header(token))
    payload = api.require_http_ok(response)
    assert_success(payload)
    state.patient_token = None

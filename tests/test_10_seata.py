from datetime import date, timedelta
from typing import Any

from conftest import assert_failure, assert_success, auth_header, ensure_patient_token
from config import TEST_PASSWORD


def _target_date() -> str:
    return (date.today() + timedelta(days=1)).isoformat()


def _ensure_patient(api: Any, state: Any) -> str:
    if state.patient_username is None:
        state.patient_username = f"test_patient_seata_{state.run_suffix}"
        register = api.request(
            "POST",
            "/user/auth/register",
            json={
                "username": state.patient_username,
                "password": TEST_PASSWORD,
                "nickname": "patient-seata",
                "phone": "13800008888",
            },
        )
        register_payload = api.require_http_ok(register)
        assert_success(register_payload)
    return ensure_patient_token(api, state)


def _ensure_doctor_context(api: Any, state: Any) -> None:
    if state.doctor_profile_id is not None and state.department_id is not None:
        return
    response = api.request("GET", "/doctor/doctor/list?pageNum=1&pageSize=10")
    payload = api.require_http_ok(response)
    assert_success(payload)
    records = (payload.get("data") or {}).get("records") or []
    assert records, "No doctors available for Seata integration tests"
    doctor_id = records[0]["id"]

    detail = api.request("GET", f"/doctor/doctor/{doctor_id}")
    detail_payload = api.require_http_ok(detail)
    assert_success(detail_payload)
    detail_data = detail_payload.get("data") or {}
    departments = detail_data.get("departments") or []
    state.doctor_profile_id = doctor_id
    state.department_id = departments[0]["id"] if departments else 1


def _load_slots(api: Any, state: Any) -> list[dict[str, Any]]:
    _ensure_doctor_context(api, state)
    response = api.request(
        "GET",
        f"/doctor/schedule/slots?doctorId={state.doctor_profile_id}&date={_target_date()}",
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    if not data:
        generate = api.request(
            "POST",
            f"/doctor/schedule/generate?startDate={_target_date()}&endDate={_target_date()}",
            headers=auth_header(state.admin_token),
        )
        generate_payload = api.require_http_ok(generate)
        assert_success(generate_payload)
        response = api.request(
            "GET",
            f"/doctor/schedule/slots?doctorId={state.doctor_profile_id}&date={_target_date()}",
        )
        payload = api.require_http_ok(response)
        assert_success(payload)
        data = payload.get("data") or []
    assert isinstance(data, list)
    return data


def _find_slot(slots: list[dict[str, Any]], slot_id: int) -> dict[str, Any]:
    slot = next((item for item in slots if item.get("id") == slot_id), None)
    assert slot is not None, f"Slot {slot_id} not found"
    return slot


def _ensure_slot_id(api: Any, state: Any) -> int:
    if state.available_slot_id is None:
        slots = _load_slots(api, state)
        assert slots, "No available slots found for Seata integration tests"
        state.available_slot_id = slots[0].get("id")
    assert state.available_slot_id is not None
    return state.available_slot_id


def _create_appointment(api: Any, token: str, state: Any) -> dict[str, Any]:
    response = api.request(
        "POST",
        "/appointment/appointment",
        headers=auth_header(token),
        json={
            "doctorId": state.doctor_profile_id,
            "departmentId": state.department_id,
            "slotId": _ensure_slot_id(api, state),
        },
    )
    payload = api.require_http_ok(response)
    return payload


def test_create_appointment_keeps_slot_and_record_in_sync(api, state) -> None:
    patient_token = _ensure_patient(api, state)
    slot_id = _ensure_slot_id(api, state)
    slot_before = _find_slot(_load_slots(api, state), slot_id)
    booked_before = slot_before.get("bookedSlots")
    assert isinstance(booked_before, int)

    payload = _create_appointment(api, patient_token, state)
    assert_success(payload)
    state.appointment_id = payload.get("data")
    assert state.appointment_id is not None

    detail_response = api.request(
        "GET",
        f"/appointment/appointment/{state.appointment_id}",
        headers=auth_header(patient_token),
    )
    detail_payload = api.require_http_ok(detail_response)
    assert_success(detail_payload)
    assert detail_payload["data"]["slotId"] == slot_id
    assert detail_payload["data"]["doctorId"] == state.doctor_profile_id

    slot_after = _find_slot(_load_slots(api, state), slot_id)
    assert slot_after.get("bookedSlots") == booked_before + 1


def test_duplicate_create_rejected_without_extra_slot_consumption(api, state) -> None:
    patient_token = _ensure_patient(api, state)
    slot_id = _ensure_slot_id(api, state)
    slot_before = _find_slot(_load_slots(api, state), slot_id)
    booked_before = slot_before.get("bookedSlots")
    assert isinstance(booked_before, int)

    duplicate_payload = _create_appointment(api, patient_token, state)
    assert_failure(duplicate_payload)

    slot_after = _find_slot(_load_slots(api, state), slot_id)
    assert slot_after.get("bookedSlots") == booked_before


def test_cancel_appointment_keeps_slot_and_status_in_sync(api, state) -> None:
    patient_token = _ensure_patient(api, state)
    assert state.appointment_id is not None
    slot_id = _ensure_slot_id(api, state)
    slot_before = _find_slot(_load_slots(api, state), slot_id)
    booked_before = slot_before.get("bookedSlots")
    assert isinstance(booked_before, int)

    cancel_response = api.request(
        "PUT",
        f"/appointment/appointment/{state.appointment_id}/cancel",
        headers=auth_header(patient_token),
    )
    cancel_payload = api.require_http_ok(cancel_response)
    assert_success(cancel_payload)

    detail_response = api.request(
        "GET",
        f"/appointment/appointment/{state.appointment_id}",
        headers=auth_header(patient_token),
    )
    detail_payload = api.require_http_ok(detail_response)
    assert_success(detail_payload)
    assert detail_payload["data"]["status"] == 2

    slot_after = _find_slot(_load_slots(api, state), slot_id)
    assert slot_after.get("bookedSlots") == booked_before - 1

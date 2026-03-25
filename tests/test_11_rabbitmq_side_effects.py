from __future__ import annotations

from datetime import date, timedelta
import os
import subprocess
import time
from typing import Any

from conftest import assert_success, auth_header, login_with_password
from config import TEST_PASSWORD


MYSQL_CONTAINER = os.getenv("MYSQL_CONTAINER", "medical-mysql")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "medical_appointment")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123")
SIDE_EFFECT_TIMEOUT_SECONDS = 30
POLL_INTERVAL_SECONDS = 1


def _target_date() -> str:
    return (date.today() + timedelta(days=1)).isoformat()


def _register_patient(api: Any, state: Any, index: int) -> str:
    digits = state.run_suffix[-7:].rjust(7, "0")
    username = f"mqp{index}_{state.run_suffix[-8:]}"
    phone = f"139{digits}{index}"
    register = api.request(
        "POST",
        "/user/auth/register",
        json={
            "username": username,
            "password": TEST_PASSWORD,
            "nickname": f"patient-rmq-{index}",
            "phone": phone,
        },
    )
    register_payload = api.require_http_ok(register)
    assert_success(register_payload)
    token, _ = login_with_password(api, username, TEST_PASSWORD)
    return token


def _ensure_doctor_context(api: Any, state: Any) -> None:
    if state.doctor_profile_id is not None and state.department_id is not None:
        return
    response = api.request("GET", "/doctor/doctor/list?pageNum=1&pageSize=10")
    payload = api.require_http_ok(response)
    assert_success(payload)
    records = (payload.get("data") or {}).get("records") or []
    assert records, "No doctors available for RabbitMQ integration tests"
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


def _ensure_slot_id(api: Any, state: Any) -> int:
    slots = _load_slots(api, state)
    available = next((item for item in slots if item.get("availableSlots", 0) > 0), None)
    assert available is not None, "No available slots found for RabbitMQ integration tests"
    slot_id = available.get("id")
    assert isinstance(slot_id, int)
    return slot_id


def _create_appointment(api: Any, token: str, state: Any) -> int:
    _ensure_doctor_context(api, state)
    slot_id = _ensure_slot_id(api, state)
    response = api.request(
        "POST",
        "/appointment/appointment",
        headers=auth_header(token),
        json={
            "doctorId": state.doctor_profile_id,
            "departmentId": state.department_id,
            "slotId": slot_id,
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    appointment_id = payload.get("data")
    assert isinstance(appointment_id, int)
    return appointment_id


def _cancel_appointment(api: Any, token: str, appointment_id: int) -> None:
    response = api.request(
        "PUT",
        f"/appointment/appointment/{appointment_id}/cancel",
        headers=auth_header(token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def _mysql_query(sql: str) -> list[list[str]]:
    command = [
        "docker",
        "exec",
        MYSQL_CONTAINER,
        "mysql",
        "-N",
        "-B",
        "-uroot",
        f"-p{MYSQL_PASSWORD}",
        "-D",
        MYSQL_DATABASE,
        "-e",
        sql,
    ]
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stderr or result.stdout
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    return [line.split("\t") for line in lines]


def _fetch_outbox_row(appointment_id: int, event_type: str) -> dict[str, Any] | None:
    rows = _mysql_query(
        " ".join(
            [
                "SELECT id, publish_status, retry_count, COALESCE(last_error, ''), routing_key",
                "FROM appointment_event_outbox",
                f"WHERE appointment_id = {appointment_id}",
                f"AND event_type = '{event_type}'",
                "ORDER BY id DESC LIMIT 1;",
            ]
        )
    )
    if not rows:
        return None
    row = rows[0]
    return {
        "event_id": int(row[0]),
        "publish_status": int(row[1]),
        "retry_count": int(row[2]),
        "last_error": row[3],
        "routing_key": row[4],
    }


def _fetch_count(table_name: str, event_id: int, field_name: str, field_value: str) -> int:
    rows = _mysql_query(
        f"SELECT COUNT(*) FROM {table_name} WHERE event_id = {event_id} AND {field_name} = '{field_value}';"
    )
    assert rows, f"No result returned for {table_name} count query"
    return int(rows[0][0])


def _wait_for_side_effects(appointment_id: int, event_type: str, routing_key: str) -> dict[str, Any]:
    deadline = time.time() + SIDE_EFFECT_TIMEOUT_SECONDS
    while time.time() < deadline:
        outbox = _fetch_outbox_row(appointment_id, event_type)
        if outbox and outbox["publish_status"] == 1:
            notification_count = _fetch_count(
                "appointment_notification_record", outbox["event_id"], "notification_type", event_type
            )
            audit_count = _fetch_count(
                "appointment_audit_record", outbox["event_id"], "action_type", event_type
            )
            if notification_count >= 1 and audit_count >= 1:
                outbox["notification_count"] = notification_count
                outbox["audit_count"] = audit_count
                return outbox
        time.sleep(POLL_INTERVAL_SECONDS)

    outbox = _fetch_outbox_row(appointment_id, event_type)
    assert outbox is not None, f"No outbox row found for appointment {appointment_id}, event {event_type}"
    assert outbox["routing_key"] == routing_key, outbox
    assert outbox["publish_status"] == 1, outbox
    notification_count = _fetch_count(
        "appointment_notification_record", outbox["event_id"], "notification_type", event_type
    )
    audit_count = _fetch_count("appointment_audit_record", outbox["event_id"], "action_type", event_type)
    assert notification_count >= 1, outbox
    assert audit_count >= 1, outbox
    outbox["notification_count"] = notification_count
    outbox["audit_count"] = audit_count
    return outbox


def test_create_appointment_publishes_outbox_and_side_effects(api: Any, state: Any) -> None:
    patient_token = _register_patient(api, state, 1)
    appointment_id = _create_appointment(api, patient_token, state)

    try:
        outbox = _wait_for_side_effects(appointment_id, "APPOINTMENT_CREATED", "appointment.created")
        assert outbox["routing_key"] == "appointment.created"
        assert outbox["retry_count"] == 0
        assert outbox["last_error"] == ""
        assert outbox["notification_count"] >= 1
        assert outbox["audit_count"] >= 1
    finally:
        _cancel_appointment(api, patient_token, appointment_id)


def test_cancel_appointment_publishes_outbox_and_side_effects(api: Any, state: Any) -> None:
    patient_token = _register_patient(api, state, 2)
    appointment_id = _create_appointment(api, patient_token, state)

    _cancel_appointment(api, patient_token, appointment_id)

    outbox = _wait_for_side_effects(appointment_id, "APPOINTMENT_CANCELLED", "appointment.cancelled")
    assert outbox["routing_key"] == "appointment.cancelled"
    assert outbox["retry_count"] == 0
    assert outbox["last_error"] == ""
    assert outbox["notification_count"] >= 1
    assert outbox["audit_count"] >= 1

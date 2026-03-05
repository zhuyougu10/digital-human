from datetime import date, timedelta

from conftest import assert_success, auth_header, ensure_patient_token
from config import TEST_PASSWORD


def test_full_patient_journey(api, state):
    if state.patient_username is None:
        state.patient_username = f"test_patient_e2e_{state.run_suffix}"
        register = api.request(
            "POST",
            "/user/auth/register",
            json={
                "username": state.patient_username,
                "password": TEST_PASSWORD,
                "nickname": "patient-e2e",
                "phone": "13800009999",
            },
        )
        register_payload = api.require_http_ok(register)
        assert_success(register_payload)

    patient_token = ensure_patient_token(api, state)

    create_session = api.request(
        "POST",
        "/ai/chat/session",
        headers=auth_header(patient_token),
        json={"sessionType": "TRIAGE"},
    )
    create_payload = api.require_http_ok(create_session)
    assert_success(create_payload)
    session_id = create_payload["data"]["id"]

    sse_resp = api.request(
        "POST",
        "/ai/chat/send",
        headers=auth_header(patient_token),
        json={"sessionId": session_id, "message": "I have persistent headache.", "sessionType": "TRIAGE"},
        stream=True,
    )
    assert sse_resp.status_code == 200
    sse_content_type = sse_resp.headers.get("Content-Type", "")
    assert ("text/event-stream" in sse_content_type) or ("application/json" in sse_content_type)
    sse_resp.close()

    list_doctors = api.request("GET", "/doctor/doctor/list?pageNum=1&pageSize=10")
    list_payload = api.require_http_ok(list_doctors)
    assert_success(list_payload)
    doctor_records = (list_payload.get("data") or {}).get("records") or []
    assert doctor_records
    target_date = (date.today() + timedelta(days=1)).isoformat()
    appointment_id = None
    for doctor in doctor_records:
        doctor_id = doctor["id"]
        detail_doctor = api.request("GET", f"/doctor/doctor/{doctor_id}")
        detail_payload = api.require_http_ok(detail_doctor)
        assert_success(detail_payload)
        departments = detail_payload["data"].get("departments") or []
        department_id = departments[0]["id"] if departments else (state.department_id or 1)

        slots_resp = api.request(
            "GET",
            f"/doctor/schedule/slots?doctorId={doctor_id}&date={target_date}",
        )
        slots_payload = api.require_http_ok(slots_resp)
        assert_success(slots_payload)
        slots = slots_payload.get("data") or []
        if not slots:
            generate = api.request(
                "POST",
                f"/doctor/schedule/generate?startDate={target_date}&endDate={target_date}",
                headers=auth_header(state.admin_token),
            )
            generate_payload = api.require_http_ok(generate)
            assert_success(generate_payload)
            slots_resp = api.request(
                "GET",
                f"/doctor/schedule/slots?doctorId={doctor_id}&date={target_date}",
            )
            slots_payload = api.require_http_ok(slots_resp)
            assert_success(slots_payload)
            slots = slots_payload.get("data") or []
        for slot in slots:
            create_appt = api.request(
                "POST",
                "/appointment/appointment",
                headers=auth_header(patient_token),
                json={
                    "doctorId": doctor_id,
                    "departmentId": department_id,
                    "slotId": slot["id"],
                    "sessionId": session_id,
                },
            )
            appt_payload = api.require_http_ok(create_appt)
            if appt_payload.get("code") == 4003:
                continue
            assert_success(appt_payload)
            appointment_id = appt_payload["data"]
            break
        if appointment_id is not None:
            break

    assert appointment_id is not None

    appt_detail = api.request(
        "GET",
        f"/appointment/appointment/{appointment_id}",
        headers=auth_header(patient_token),
    )
    appt_detail_payload = api.require_http_ok(appt_detail)
    assert_success(appt_detail_payload)

    cancel = api.request(
        "PUT",
        f"/appointment/appointment/{appointment_id}/cancel",
        headers=auth_header(patient_token),
    )
    cancel_payload = api.require_http_ok(cancel)
    assert_success(cancel_payload)

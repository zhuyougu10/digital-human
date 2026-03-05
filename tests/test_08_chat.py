from conftest import assert_success, auth_header, ensure_patient_token


def test_create_session(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "POST",
        "/ai/chat/session",
        headers=auth_header(patient_token),
        json={"sessionType": "TRIAGE"},
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    state.chat_session_id = payload["data"]["id"]
    assert payload["data"]["sessionType"] == "TRIAGE"


def test_list_sessions(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "GET",
        "/ai/chat/sessions",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert any(item.get("id") == state.chat_session_id for item in payload.get("data") or [])


def test_sse_send_message(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "POST",
        "/ai/chat/send",
        headers=auth_header(patient_token),
        json={"sessionId": state.chat_session_id, "message": "I have a headache.", "sessionType": "TRIAGE"},
        stream=True,
    )
    assert response.status_code == 200
    content_type = response.headers.get("Content-Type", "")
    assert ("text/event-stream" in content_type) or ("application/json" in content_type)

    if "text/event-stream" in content_type:
        from sseclient import SSEClient

        client = SSEClient(response)
        got_event = False
        for index, event in enumerate(client.events()):
            if event.data is not None:
                got_event = True
                break
            if index > 10:
                break
        response.close()
        assert got_event
    else:
        response.close()


def test_message_history(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "GET",
        f"/ai/chat/session/{state.chat_session_id}/messages",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_end_session(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "POST",
        f"/ai/chat/session/{state.chat_session_id}/end",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_delete_session(api, state):
    patient_token = ensure_patient_token(api, state)
    response = api.request(
        "DELETE",
        f"/ai/chat/session/{state.chat_session_id}",
        headers=auth_header(patient_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)

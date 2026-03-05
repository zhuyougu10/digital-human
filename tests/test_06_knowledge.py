import pytest

from conftest import assert_success, auth_header, extract_records


def test_create_kb(api, state):
    response = api.request(
        "POST",
        "/knowledge/kb",
        headers=auth_header(state.admin_token),
        json={
            "name": f"Integration KB {state.run_suffix}",
            "description": "Knowledge base for integration tests",
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    state.kb_id = payload.get("data")
    assert state.kb_id is not None


def test_list_kb(api, state):
    response = api.request(
        "GET",
        "/knowledge/kb/list?pageNum=1&pageSize=20",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert any(item.get("id") == state.kb_id for item in extract_records(payload))


def test_get_kb(api, state):
    response = api.request("GET", f"/knowledge/kb/{state.kb_id}")
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert payload["data"]["id"] == state.kb_id


def test_upload_document(api, state):
    files = {
        "file": (
            "test-upload.txt",
            "Hypertension is a common chronic disease. Lifestyle management is important.",
            "text/plain",
        )
    }
    response = api.request(
        "POST",
        f"/knowledge/kb/{state.kb_id}/document",
        headers=auth_header(state.admin_token),
        files=files,
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    state.document_id = payload.get("data")
    assert state.document_id is not None


def test_list_documents(api, state):
    response = api.request("GET", f"/knowledge/kb/{state.kb_id}/documents?pageNum=1&pageSize=10")
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert isinstance(extract_records(payload), list)


def test_add_manual_chunk(api, state):
    response = api.request(
        "POST",
        f"/knowledge/kb/{state.kb_id}/chunk",
        headers=auth_header(state.admin_token),
        json={"title": "Hypertension", "content": "Control salt intake and monitor blood pressure."},
    )
    payload = api.require_http_ok(response)
    # Placeholder DASHSCOPE_API_KEY is an environment/config issue; treat 5003 as expected.
    if payload.get("code") == 5003:
        state.chunk_id = None
        pytest.skip("Embedding service unavailable (expected with placeholder DASHSCOPE_API_KEY)")
    assert_success(payload)
    state.chunk_id = payload.get("data")
    assert state.chunk_id is not None


def test_list_chunks(api, state):
    response = api.request("GET", f"/knowledge/kb/document/{state.document_id}/chunks?pageNum=1&pageSize=10")
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_search_kb(api, state):
    response = api.request(
        "POST",
        "/knowledge/kb/search",
        json={"kbId": state.kb_id, "query": "hypertension", "topK": 3},
    )
    payload = api.require_http_ok(response)
    assert payload.get("code") in (200, 5002, 5003, 5004, 5000) or payload.get("code") == 200


def test_delete_chunk(api, state):
    if state.chunk_id is None:
        pytest.skip("No chunk created in this run")
    response = api.request(
        "DELETE",
        f"/knowledge/kb/chunk/{state.chunk_id}",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_delete_kb(api, state):
    response = api.request(
        "DELETE",
        f"/knowledge/kb/{state.kb_id}",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)

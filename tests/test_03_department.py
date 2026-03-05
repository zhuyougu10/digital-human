from conftest import assert_success, auth_header


def test_list_departments(api, state):
    response = api.request("GET", "/doctor/department/list")
    payload = api.require_http_ok(response)
    assert_success(payload)
    data = payload.get("data") or []
    assert isinstance(data, list)
    assert len(data) >= 1
    if data and state.department_id is None:
        state.department_id = data[0].get("id")


def test_create_department(api, state):
    state.department_name = f"Integration Dept {state.run_suffix}"
    response = api.request(
        "POST",
        "/doctor/department",
        headers=auth_header(state.admin_token),
        json={
            "name": state.department_name,
            "description": "Department for integration tests",
            "icon": "stethoscope",
            "sort": 99,
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)

    search = api.request("GET", f"/doctor/department/list?keyword={state.department_name}")
    search_payload = api.require_http_ok(search)
    assert_success(search_payload)
    data = search_payload.get("data") or []
    matched = next((x for x in data if x.get("name") == state.department_name), None)
    assert matched is not None
    state.department_id = matched.get("id")


def test_get_department(api, state):
    response = api.request("GET", f"/doctor/department/{state.department_id}")
    payload = api.require_http_ok(response)
    assert_success(payload)
    assert payload["data"]["id"] == state.department_id


def test_update_department(api, state):
    response = api.request(
        "PUT",
        f"/doctor/department/{state.department_id}",
        headers=auth_header(state.admin_token),
        json={
            "name": f"{state.department_name} Updated",
            "description": "Updated by integration test",
            "icon": "heart",
            "sort": 100,
        },
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_disable_department(api, state):
    response = api.request(
        "PUT",
        f"/doctor/department/{state.department_id}/toggle-status",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_enable_department(api, state):
    response = api.request(
        "PUT",
        f"/doctor/department/{state.department_id}/toggle-status",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)


def test_delete_department(api, state):
    response = api.request(
        "DELETE",
        f"/doctor/department/{state.department_id}",
        headers=auth_header(state.admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    # The created integration department is deleted here; force downstream tests
    # to fetch a fresh active department instead of reusing a stale ID.
    state.department_id = None

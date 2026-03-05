from dataclasses import dataclass
import os
import time
from typing import Any

import pytest
import requests

from config import (
    ADMIN_PASSWORD,
    ADMIN_USERNAME,
    BASE_URL,
    FALLBACK_BASE_URL,
    REQUEST_TIMEOUT,
    TEST_PASSWORD,
)


@dataclass
class SharedState:
    admin_token: str | None = None
    admin_user_id: int | None = None
    patient_username: str | None = None
    patient_user_id: int | None = None
    patient_token: str | None = None
    doctor_username: str | None = None
    doctor_user_id: int | None = None
    doctor_token: str | None = None
    department_id: int | None = None
    department_name: str | None = None
    doctor_profile_id: int | None = None
    schedule_template_id: int | None = None
    available_slot_id: int | None = None
    available_slot_date: str | None = None
    kb_id: int | None = None
    document_id: int | None = None
    chunk_id: int | None = None
    appointment_id: int | None = None
    chat_session_id: int | None = None
    run_suffix: str = str(int(time.time()))


class ApiClient:
    def __init__(self) -> None:
        preferred = os.getenv("BASE_URL", BASE_URL)
        candidates = [preferred, BASE_URL, FALLBACK_BASE_URL]
        self.base_urls: list[str] = []
        for item in candidates:
            if item and item not in self.base_urls:
                self.base_urls.append(item)
        self.active_base_url = self.base_urls[0]
        self.default_token: str | None = None

    def request(self, method: str, path: str, **kwargs: Any) -> requests.Response:
        kwargs.setdefault("timeout", REQUEST_TIMEOUT)
        headers = kwargs.get("headers")
        if headers is None:
            headers = {}
        else:
            headers = dict(headers)

        has_auth = any(key.lower() == "authorization" for key in headers)
        if not has_auth and self.default_token:
            headers["Authorization"] = f"Bearer {self.default_token}"
        kwargs["headers"] = headers

        last_exc: Exception | None = None
        for base_url in [self.active_base_url, *[u for u in self.base_urls if u != self.active_base_url]]:
            try:
                response = requests.request(method=method, url=f"{base_url}{path}", **kwargs)
                self.active_base_url = base_url
                return response
            except requests.exceptions.ConnectionError as exc:
                last_exc = exc
                continue
        assert last_exc is not None
        raise last_exc

    def json(self, response: requests.Response) -> dict[str, Any]:
        return response.json()

    def require_http_ok(self, response: requests.Response) -> dict[str, Any]:
        assert response.status_code == 200, response.text
        return self.json(response)


def auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def assert_success(payload: dict[str, Any]) -> None:
    assert payload.get("code") == 200, payload


def assert_failure(payload: dict[str, Any]) -> None:
    assert payload.get("code") != 200, payload


def login_with_password(api: ApiClient, username: str, password: str) -> tuple[str, dict[str, Any]]:
    response = api.request(
        "POST",
        "/user/auth/login",
        json={"username": username, "password": password},
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    token = payload["data"]["token"]
    user = payload["data"]["user"]
    return token, user


def ensure_patient_token(api: ApiClient, state: SharedState) -> str:
    if state.patient_token:
        return state.patient_token
    assert state.patient_username is not None
    token, user = login_with_password(api, state.patient_username, TEST_PASSWORD)
    state.patient_token = token
    state.patient_user_id = user.get("id")
    return token


def ensure_doctor_token(api: ApiClient, state: SharedState) -> str:
    if state.doctor_token:
        return state.doctor_token
    assert state.doctor_username is not None
    token, user = login_with_password(api, state.doctor_username, TEST_PASSWORD)
    state.doctor_token = token
    state.doctor_user_id = user.get("id")
    return token


def extract_records(payload: dict[str, Any]) -> list[dict[str, Any]]:
    data = payload.get("data") or {}
    records = data.get("records")
    return records if isinstance(records, list) else []


def find_user_id_by_username(api: ApiClient, admin_token: str, username: str) -> int | None:
    response = api.request(
        "GET",
        f"/user/user/list?pageNum=1&pageSize=50&keyword={username}",
        headers=auth_header(admin_token),
    )
    payload = api.require_http_ok(response)
    assert_success(payload)
    for item in extract_records(payload):
        if item.get("username") == username:
            return item.get("id")
    return None


@pytest.fixture(scope="session")
def state() -> SharedState:
    return SharedState()


@pytest.fixture(scope="session")
def api() -> ApiClient:
    return ApiClient()


@pytest.fixture(scope="session", autouse=True)
def admin_login(state: SharedState, api: ApiClient) -> None:
    token, user = login_with_password(api, ADMIN_USERNAME, ADMIN_PASSWORD)
    state.admin_token = token
    state.admin_user_id = user.get("id")
    api.default_token = token

import pytest

import app.auth
from app.config import Settings


@pytest.fixture(autouse=True)
def local_jwt_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        app.auth,
        "settings",
        Settings(jwt_secret="test-jwt-secret-test-jwt-secret-123456"),
    )

from datetime import datetime, timedelta, timezone

import jwt
from fastapi.testclient import TestClient

from app.main import app
from app.config import settings


client = TestClient(app)


def token(roles: list[str]) -> str:
    return jwt.encode(
        {
            "sub": "analyst-1",
            "roles": roles,
            "exp": datetime.now(timezone.utc) + timedelta(minutes=5),
        },
        settings.jwt_secret or "test-jwt-secret-test-jwt-secret-123456",
        algorithm="HS256",
    )


def headers(roles: list[str]) -> dict[str, str]:
    return {"Authorization": f"Bearer {token(roles)}"}


def test_live_health_does_not_require_reporting_data() -> None:
    response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_analytics_requires_a_token() -> None:
    response = client.get("/api/analytics/summary", params={"from": "2026-10-01", "to": "2026-10-02"})
    assert response.status_code == 401


def test_analytics_requires_analyst_role() -> None:
    response = client.get(
        "/api/analytics/summary",
        params={"from": "2026-10-01", "to": "2026-10-02"},
        headers=headers(["CLIENT"]),
    )
    assert response.status_code == 403


def test_analyst_sees_explicit_unavailable_state() -> None:
    response = client.get(
        "/api/analytics/summary",
        params={"from": "2026-10-01", "to": "2026-10-02"},
        headers=headers(["ANALYST"]),
    )
    assert response.status_code == 503
    assert "reporting source" in response.json()["detail"]


def test_invalid_date_order_is_rejected_before_reporting_query() -> None:
    response = client.get(
        "/api/analytics/trends",
        params={"from": "2026-10-03", "to": "2026-10-02"},
        headers=headers(["ANALYST"]),
    )
    assert response.status_code == 422


def test_portfolio_visualization_endpoints_keep_the_reporting_boundary() -> None:
    for path in ("/api/analytics/portfolio-performance", "/api/analytics/holdings"):
        response = client.get(
            path,
            params={"from": "2026-10-01", "to": "2026-10-02"},
            headers=headers(["ANALYST"]),
        )
        assert response.status_code == 503
        assert "reporting source" in response.json()["detail"]

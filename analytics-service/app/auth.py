from dataclasses import dataclass
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import settings


bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class CurrentAnalyst:
    subject: str
    roles: tuple[str, ...]


def _unauthorized() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="A valid analyst session is required.",
        headers={"WWW-Authenticate": "Bearer"},
    )


def get_current_analyst(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> CurrentAnalyst:
    if credentials is None or credentials.scheme.lower() != "bearer" or not settings.jwt_secret:
        raise _unauthorized()
    try:
        decode_options = {"algorithms": ["HS256"], "options": {"require": ["exp", "sub"]}}
        if settings.jwt_issuer:
            decode_options["issuer"] = settings.jwt_issuer
        if settings.jwt_audience:
            decode_options["audience"] = settings.jwt_audience
        claims = jwt.decode(credentials.credentials, settings.jwt_secret, **decode_options)
    except (jwt.PyJWTError, TypeError, ValueError):
        raise _unauthorized() from None

    roles_claim = claims.get("roles", [])
    roles = (roles_claim,) if isinstance(roles_claim, str) else tuple(roles_claim)
    if "ANALYST" not in roles:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Analyst access is required.")
    return CurrentAnalyst(subject=str(claims["sub"]), roles=roles)

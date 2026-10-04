from pathlib import Path
from typing import Annotated

from pydantic import field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict


class Settings(BaseSettings):
    """Configuration, read from IRIS_* environment variables and an optional .env file."""

    model_config = SettingsConfigDict(env_prefix="IRIS_", env_file=".env", extra="ignore")

    host: str = "0.0.0.0"
    port: int = 8080
    env: str = "local"
    data_dir: Path = Path("./data")
    scan_hosts: Annotated[list[str], NoDecode] = []
    static_dir: Path | None = None
    auth_token: str | None = None
    log_level: str = "info"
    command_timeout: float = 5.0

    @field_validator("scan_hosts", mode="before")
    @classmethod
    def _split_hosts(cls, value: object) -> object:
        if isinstance(value, str):
            return [host.strip() for host in value.split(",") if host.strip()]
        return value

    @field_validator("static_dir", "auth_token", mode="before")
    @classmethod
    def _empty_is_none(cls, value: object) -> object:
        return None if value == "" else value

    @field_validator("data_dir", "static_dir", mode="after")
    @classmethod
    def _expand_home(cls, value: Path | None) -> Path | None:
        return value.expanduser() if value else value

    @property
    def storage_file(self) -> Path:
        return self.data_dir / "pyatv.conf"

    @property
    def pairing_name(self) -> str:
        """Name this server pairs under; shown in the Apple TV's list of remotes."""
        return f"Iris ({self.env})"

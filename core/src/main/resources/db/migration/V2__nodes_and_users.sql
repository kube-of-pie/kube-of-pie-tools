CREATE TABLE nodes (
    id               TEXT PRIMARY KEY,
    hostname         TEXT,
    dns              TEXT,
    model            TEXT,
    wifi_enabled     INTEGER,
    wifi_ssid        TEXT,
    wifi_passphrase  TEXT
);

CREATE TABLE users (
    name             TEXT PRIMARY KEY,
    password         TEXT,
    ssh_enabled      INTEGER,
    ssh_private_key  TEXT,
    ssh_public_key   TEXT
);

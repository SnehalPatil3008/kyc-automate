CREATE TABLE verification_results (
    verification_id VARCHAR2(36) PRIMARY KEY,
    application_id VARCHAR2(36) NOT NULL,
    decision VARCHAR2(40) NOT NULL,
    reason VARCHAR2(2000),
    name_score FLOAT,
    address_score FLOAT,
    evidence_json CLOB,
    verified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_verification_application ON verification_results(application_id, verified_at);

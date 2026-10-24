CREATE TABLE verification_results (
    verification_id VARCHAR2(36) PRIMARY KEY,
    application_id VARCHAR2(36) NOT NULL,
    decision VARCHAR2(40) NOT NULL,
    reason VARCHAR2(2000),
    name_score FLOAT,
    address_score FLOAT,
    evidence_json CLOB,
    customer_name VARCHAR2(200),
	customer_dob VARCHAR2(30),
	customer_address VARCHAR2(1000),
    verified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_verification_application ON verification_results(application_id, verified_at);

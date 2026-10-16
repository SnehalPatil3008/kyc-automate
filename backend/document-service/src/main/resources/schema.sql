CREATE TABLE documents (
    document_id VARCHAR2(36) PRIMARY KEY,
    application_id VARCHAR2(36) NOT NULL,
    document_type VARCHAR2(30) NOT NULL,
    document_number VARCHAR2(100),
    storage_reference VARCHAR2(512) NOT NULL,
    original_filename VARCHAR2(255),
    mime_type VARCHAR2(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    processing_status VARCHAR2(30) DEFAULT 'PENDING' NOT NULL,
    processing_message VARCHAR2(1000),
    expiry_date VARCHAR2(20),
    average_confidence FLOAT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE extracted_fields (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id VARCHAR2(36) NOT NULL,
    field_name VARCHAR2(50) NOT NULL,
    field_value VARCHAR2(1000),
    confidence_score FLOAT,
    CONSTRAINT fk_extracted_document FOREIGN KEY (document_id)
        REFERENCES documents(document_id) ON DELETE CASCADE
);

CREATE INDEX idx_document_application ON documents(application_id);
CREATE INDEX idx_document_active ON documents(application_id, is_active);
CREATE INDEX idx_extracted_document ON extracted_fields(document_id);

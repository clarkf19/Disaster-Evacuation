-- PostgreSQL & H2 Compatible Schema DDL for Mumbai Evacuation Engine

CREATE TABLE IF NOT EXISTS nodes (
    id BIGINT PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS edges (
    id BIGINT PRIMARY KEY,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    road_type VARCHAR(50) NOT NULL,
    speed_limit_kmh DOUBLE PRECISION NOT NULL,
    capacity INT NOT NULL,
    current_traffic INT DEFAULT 0,
    blocked BOOLEAN DEFAULT FALSE,
    congestion_factor DOUBLE PRECISION DEFAULT 1.0,
    CONSTRAINT fk_source_node FOREIGN KEY (source_node_id) REFERENCES nodes(id),
    CONSTRAINT fk_target_node FOREIGN KEY (target_node_id) REFERENCES nodes(id)
);

CREATE TABLE IF NOT EXISTS shelters (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    nearest_node_id BIGINT NOT NULL,
    total_capacity INT NOT NULL,
    current_occupancy INT DEFAULT 0,
    CONSTRAINT fk_shelter_node FOREIGN KEY (nearest_node_id) REFERENCES nodes(id)
);

CREATE TABLE IF NOT EXISTS evacuee_groups (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    source_node_id BIGINT NOT NULL,
    count INT NOT NULL,
    assigned_shelter_id BIGINT,
    status VARCHAR(50) DEFAULT 'PENDING',
    CONSTRAINT fk_evacuee_node FOREIGN KEY (source_node_id) REFERENCES nodes(id)
);

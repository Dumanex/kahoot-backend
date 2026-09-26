ALTER TABLE players
    ADD COLUMN rejoin_token VARCHAR(36);

ALTER TABLE game_sessions
    ADD COLUMN question_started_at TIMESTAMP;

ALTER TABLE game_sessions
    ADD COLUMN question_finalized BOOLEAN NOT NULL DEFAULT FALSE;

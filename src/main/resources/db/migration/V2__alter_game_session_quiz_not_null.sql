-- V2: Make quiz_id NOT NULL in game_sessions
ALTER TABLE game_sessions ALTER COLUMN quiz_id SET NOT NULL;
-- V1: Create initial schema for Kahoot-like quiz application

-- Users table (quiz creators only)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Quizzes table
CREATE TABLE quizzes (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    creator_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    time_per_question INTEGER DEFAULT 20,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Questions table
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT REFERENCES quizzes(id) ON DELETE CASCADE,
    question_type VARCHAR(20) NOT NULL, -- multiple choice, true/false, image_recognition, audio
    question_text TEXT NOT NULL,
    image_url VARCHAR(500),
    audio_url VARCHAR(500),
    time_limit_seconds INTEGER,
    order_index INTEGER NOT NULL
);

-- Answers table
CREATE TABLE answers (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT REFERENCES questions(id) ON DELETE CASCADE,
    answer_text VARCHAR(500) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    order_index INTEGER NOT NULL,
    symbol VARCHAR(10), -- triangle, circle, diamonds, square
    color VARCHAR(20)   -- red, blue, yellow, green
);

-- Game sessions table (live games)
CREATE TABLE game_sessions(
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT REFERENCES quizzes(id),
    pin_code VARCHAR(6) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING', -- waiting, in_progress, completed
    current_question_index INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Players table
CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    game_session_id BIGINT REFERENCES game_sessions(id) ON DELETE CASCADE,
    nickname VARCHAR(30) NOT NULL,
    score INTEGER DEFAULT 0,
    streak INTEGER DEFAULT 0,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(game_session_id, nickname)
);

-- Player answers table
CREATE TABLE player_answers (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT REFERENCES players(id) ON DELETE CASCADE,
    question_id BIGINT REFERENCES questions(id),
    answer_id BIGINT REFERENCES answers(id),
    response_time_ms INTEGER,
    is_correct BOOLEAN,
    points_earned INTEGER DEFAULT 0,
    answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_quizzes_creator ON quizzes(creator_id);
CREATE INDEX idx_questions_quiz ON questions(quiz_id);
CREATE INDEX idx_answers_question ON answers(question_id);
CREATE INDEX idx_game_sessions_pin ON game_sessions(pin_code);
CREATE INDEX idx_players_session ON players(game_session_id);
CREATE INDEX idx_player_answers_player ON player_answers(player_id);
CREATE INDEX idx_player_answers_question ON player_answers(question_id);
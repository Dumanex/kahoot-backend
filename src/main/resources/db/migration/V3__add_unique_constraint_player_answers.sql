ALTER TABLE player_answers
    ADD CONSTRAINT uk_player_answers_player_question UNIQUE (player_id, question_id);
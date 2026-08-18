# Kahoot Backend - Implementation Plan

## Context

Real-time multiplayer quiz game backend for children aged 7-10. One player (host) controls the game flow while up to 10 players join via PIN code to answer questions on their devices.

**Technology Stack:**
- Java + Spring Boot
- Gradle (Kotlin DSL)
- PostgreSQL
- WebSocket (STOMP)
- JWT Authentication

---

## Architecture: Three Layers

```
Controller → Service → Repository
```

Simple, straightforward code suitable for academic defense.

---

## 1. Project Setup

### 1.1 Initialize Spring Boot

Use Spring Initializr or create manually with:

**Dependencies:**
- Spring Web
- Spring Data JPA
- Spring Security
- Spring WebSocket
- PostgreSQL Driver
- JWT (io.jsonwebtoken:jjwt-api, jjwt-impl, jjwt-jackson)
- Lombok (optional)
- Flyway (database migrations)
- Validation

### 1.2 Configuration

**application.yml:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kahoot
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
jwt:
  secret: your-secret-key
  expiration: 86400000
```

---

## 2. Database Schema

### 2.1 Tables

**users** - Quiz creators only
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**quizzes**
```sql
CREATE TABLE quizzes (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    creator_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    time_per_question INTEGER DEFAULT 20,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**questions**
```sql
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT REFERENCES quizzes(id) ON DELETE CASCADE,
    question_type VARCHAR(20) NOT NULL, -- MULTIPLE_CHOICE, TRUE_FALSE, IMAGE_RECOGNITION, AUDIO
    question_text TEXT NOT NULL,
    image_url VARCHAR(500),
    audio_url VARCHAR(500),
    time_limit_seconds INTEGER,
    order_index INTEGER NOT NULL
);
```

**answers**
```sql
CREATE TABLE answers (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT REFERENCES questions(id) ON DELETE CASCADE,
    answer_text VARCHAR(500) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    order_index INTEGER NOT NULL,
    symbol VARCHAR(10), -- triangle, circle, diamond, square
    color VARCHAR(20)   -- red, blue, yellow, green
);
```

**game_sessions** - Live games
```sql
CREATE TABLE game_sessions (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT REFERENCES quizzes(id),
    pin_code VARCHAR(6) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING', -- WAITING, IN_PROGRESS, COMPLETED
    current_question_index INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**players**
```sql
CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    game_session_id BIGINT REFERENCES game_sessions(id) ON DELETE CASCADE,
    nickname VARCHAR(30) NOT NULL,
    score INTEGER DEFAULT 0,
    streak INTEGER DEFAULT 0,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(game_session_id, nickname)
);
```

**player_answers**
```sql
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
```

---

## 3. Entity Classes (Model Layer)

Simple POJOs with JPA annotations:

- `User.java`
- `Quiz.java`
- `Question.java`
- `Answer.java`
- `GameSession.java`
- `Player.java`
- `PlayerAnswer.java`

---

## 4. Repository Layer

Extend `JpaRepository`:

- `UserRepository extends JpaRepository<User, Long>`
- `QuizRepository extends JpaRepository<Quiz, Long>`
- `QuestionRepository extends JpaRepository<Question, Long>`
- `AnswerRepository extends JpaRepository<Answer, Long>`
- `GameSessionRepository extends JpaRepository<GameSession, Long>`
- `PlayerRepository extends JpaRepository<Player, Long>`
- `PlayerAnswerRepository extends JpaRepository<PlayerAnswer, Long>`

---

## 5. Service Layer

### 5.1 AuthService
- `register(username, email, password)` - Register new user
- `login(username, password)` - Validate credentials, return JWT
- Password hashing with BCrypt

### 5.2 QuizService
- `createQuiz(userId, title, description)`
- `getQuiz(id)`
- `updateQuiz(id, ...)`
- `deleteQuiz(id)`
- `addQuestion(quizId, questionData)`
- `updateQuestion(id, ...)`
- `deleteQuestion(id)`

### 5.3 GameService
- `createSession(quizId)` - Generate 6-digit PIN, create GameSession
- `getSession(pin)` - Get session info
- `startGame(pin)` - Change status to IN_PROGRESS
- `nextQuestion(pin)` - Advance question index
- `endGame(pin)` - Change status to COMPLETED

### 5.4 PlayerService
- `joinGame(pin, nickname)` - Add player to session
- `submitAnswer(playerId, questionId, answerId, responseTime)`

### 5.5 ScoringService
```
Points = 1000 (base) + 500 * (1 - responseTime / timeLimit) + streak * 50
```
- Base: 1000 for correct answer
- Speed bonus: up to 500 (faster = more)
- Streak bonus: +50 per consecutive correct

---

## 6. Controller Layer (REST API)

### 6.1 AuthController
```
POST /api/auth/register
POST /api/auth/login      → Returns JWT
```

### 6.2 QuizController
```
GET    /api/quizzes           → List user's quizzes
POST   /api/quizzes           → Create quiz
GET    /api/quizzes/{id}      → Get quiz with questions
PUT    /api/quizzes/{id}      → Update quiz
DELETE /api/quizzes/{id}      → Delete quiz
```

### 6.3 QuestionController
```
GET    /api/quizzes/{quizId}/questions
POST   /api/quizzes/{quizId}/questions
PUT    /api/questions/{id}
DELETE /api/questions/{id}
```

### 6.4 GameController
```
POST /api/games/host          → Create session, returns PIN
GET  /api/games/{pin}         → Get session info
POST /api/games/{pin}/start   → Start game
```

---

## 7. WebSocket (STOMP)

### 7.1 Configuration
- Endpoint: `/ws/game`
- Enable SockJS fallback

### 7.2 Topics (Server → Client)
```
/topic/game/{pin}/lobby       → Player joined
/topic/game/{pin}/question    → New question broadcast
/topic/game/{pin}/timer       → Countdown ticks
/topic/game/{pin}/results     → Question results
/topic/game/{pin}/leaderboard → Current rankings
```

### 7.3 Message Handlers (Client → Server)
- Join game (nickname)
- Submit answer (questionId, answerId, timestamp)
- Next question (host only)

---

## 8. Security (JWT)

- Filter that validates JWT on each request
- Public endpoints: `/api/auth/**`, `/api/games/{pin}`
- Protected endpoints: Everything else requires valid JWT
- Players don't need JWT - only quiz creators do

---

## 9. Implementation Order

1. **Setup**
   - Create Spring Boot project with Gradle
   - Configure PostgreSQL connection
   - Set up Flyway migrations

2. **Authentication**
   - User entity
   - UserRepository
   - AuthService (register, login, JWT)
   - AuthController
   - JWT filter

3. **Quiz CRUD**
   - Quiz, Question, Answer entities
   - Repositories
   - QuizService
   - QuizController, QuestionController

4. **Test with Postman**
   - Verify all CRUD operations
   - Verify JWT protection

5. **Game Session**
   - GameSession entity
   - PIN generation logic
   - GameService, GameController

6. **WebSocket**
   - WebSocketConfig
   - Session manager (track active games in memory)
   - Message handlers

7. **Game Logic**
   - Question broadcasting
   - Timer synchronization
   - Answer submission

8. **Scoring**
   - ScoringService
   - Leaderboard calculation

---

## 10. Testing with Postman

Before moving to frontend, verify:

- [ ] `POST /api/auth/register` - Creates user
- [ ] `POST /api/auth/login` - Returns JWT
- [ ] `GET /api/quizzes` with JWT - Returns user's quizzes
- [ ] `POST /api/quizzes` - Creates quiz
- [ ] `POST /api/quizzes/{id}/questions` - Adds question
- [ ] `POST /api/games/host` - Returns PIN
- [ ] `GET /api/games/{pin}` - Returns session info

---

## 11. Commit Messages

Suggested commits after each logical unit:

- `feat: Initialize Spring Boot project with Gradle`
- `feat: Add user authentication with JWT`
- `feat: Add quiz and question management`
- `feat: Add game session with PIN generation`
- `feat: Add WebSocket for real-time communication`
- `feat: Add scoring system and leaderboard`

---

## Notes

- Keep code simple and readable
- Avoid over-engineering
- Be ready to explain each component to professor
- Test each feature with Postman before moving on

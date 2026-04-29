# SmartVoyage Messaging API

Production-oriented Spring Boot module for real-time messaging (travelers ↔ agencies).

## Run

```bash
cd smartvoyage-messaging-api
mvn spring-boot:run
```

## WebSocket

- STOMP endpoint: `/ws`
- Subscribe conversation: `/topic/conversations/{id}`
- User queue: `/user/queue/messages`
- Send message destination: `/app/conversations/{id}/send`
- Typing destination: `/app/conversations/{id}/typing`

Socket send payload:

```json
{
  "userId": 18,
  "content": "Can you send me the programme and devis?"
}
```

## REST APIs

### Conversations
- `POST /api/conversations?eventId=55&travelerUserId=18&agencyUserId=72`
- `GET /api/conversations?userId=18`

### Messages
- `GET /api/conversations/{id}/messages?page=0&size=20&userId=18`
- `POST /api/conversations/{id}/messages?userId=18`
- `PATCH /api/messages/{messageId}?userId=18`
- `DELETE /api/messages/{messageId}?userId=18`
- `GET /api/messages/search?keyword=hotel&userId=18&page=0&size=20`
- `PATCH /api/messages/{id}/delivered?userId=18`
- `PATCH /api/messages/{id}/read?userId=18`

`POST /api/conversations/{id}/messages` body:

```json
{
  "content": "Hello, can I get a pricing quote?",
  "type": "TEXT"
}
```

### Follow-ups
- `GET /api/conversations/{id}/follow-ups?userId=18`
- `POST /api/conversations/{id}/follow-ups?userId=18`
- `PATCH /api/follow-ups/{id}?userId=18`
- `PATCH /api/follow-ups/{id}/done?userId=18`
- `DELETE /api/follow-ups/{id}?userId=18`

Create follow-up body:

```json
{
  "title": "Send detailed devis",
  "description": "Include transport + accommodation",
  "assignedToUserId": 72,
  "priority": "HIGH",
  "dueDate": "2026-04-30T10:00:00"
}
```

### File/Image/Audio Upload
- `POST /api/messages/upload` (multipart)
- `POST /api/messages/voice` (multipart, audio only)
- `GET /api/files/{filename}?conversationId=99&userId=18`

### Notifications
- `GET /api/notifications?userId=18&size=20`
- `GET /api/notifications/unread-count?userId=18`

### Presence / Stats
- `POST /api/presence/online?userId=18&online=true`
- `GET /api/stats/messages`

## Advanced Features Implemented

- Real-time STOMP messaging + user queue push
- Twilio service (SMS/WhatsApp hooks)
- Anti-spam/rate limiting
  - max 5 msgs / 10 seconds per user
  - duplicate detector
  - bad-word filter
- Automatic follow-up creation from keywords
  - programme, devis/prix, reservation/book
- Message statuses: SENT / DELIVERED / READ
- Typing indicator over WebSocket
- File/image/audio upload + secure participant download
- Notification smart batching (3s) with grouped message

## Notes

- Current security model is participant-based using `userId` request param plus conversation membership checks.
- For production hardening, replace it with JWT auth and derive userId from token.

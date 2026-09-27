# Agentic Enterprise UI

A polished React + TypeScript frontend for the existing Spring Boot Agentic AI Chatbot POC.

## Connect to the backend

Default API:
`http://localhost:8080/api/chat`

Override it with:

```bash
VITE_API_URL=http://localhost:8080/api/chat
```

## Run

```bash
npm install
npm run dev
```

Open the Vite URL shown in the terminal.

The frontend sends:

```json
{
  "sessionId": "ui-...",
  "message": "Who is employee 101?"
}
```

to the existing `POST /api/chat` endpoint.

## What is included

- Enterprise-style dark UI
- Responsive layout
- Chat history for the active session
- Existing backend integration
- Supervisor activity visualization
- Database / RAG / Web / LLM tool cards
- Source and intent badges from `ChatResponse`
- Loading state
- Error state
- Dark/light mode
- Quick-start prompts

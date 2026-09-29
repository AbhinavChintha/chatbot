import { useEffect, useMemo, useState } from "react";
import {
  Bot, ChevronRight, Database, FileText, Globe2, Menu, Moon,
  Plus, Search, Send, Settings2, Sparkles, Sun, X, Zap
} from "lucide-react";

type ApiResponse = {
  answer: string;
  source?: string;
  type?: string;
  success?: boolean;
  data?: unknown;
};

type Message = {
  id: number;
  role: "user" | "assistant";
  text: string;
  meta?: ApiResponse;
  loading?: boolean;
};

type ConversationSummary = {
  sessionId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
};

type ConversationMessage = {
  id: number;
  sessionId: string;
  role: string;
  message: string;
  createdAt: string;
};

// @ts-ignore
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api/chat";

const examples = [
  "Who is employee 101?",
  "What is our annual leave policy?",
  "What is the latest information about Spring AI?",
  "Give me employee 101's role, the annual leave policy and the latest Spring AI information."
];

function sourceIcon(source = "") {
  const s = source.toLowerCase();
  if (s.includes("database")) return <Database size={14} />;
  if (s.includes("document") || s.includes("rag")) return <FileText size={14} />;
  if (s.includes("web")) return <Globe2 size={14} />;
  return <Sparkles size={14} />;
}

function parseAnswer(text: string) {
  return text.split("\n").map((line, i) => {
    const trimmed = line.trim();
    if (!trimmed) return <div key={i} className="answer-gap" />;
    if (trimmed.startsWith("**") && trimmed.endsWith("**")) {
      return <h3 key={i}>{trimmed.replace(/\*\*/g, "")}</h3>;
    }
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      return <div key={i} className="answer-bullet">• {trimmed.slice(2)}</div>;
    }
    const bold = trimmed.replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>");
    return <p key={i} dangerouslySetInnerHTML={{ __html: bold }} />;
  });
}

function App() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [sessionId, setSessionId] = useState(() => `ui-${Date.now()}`);
  const [dark, setDark] = useState(true);
  const [showActivity, setShowActivity] = useState(true);
  const [busy, setBusy] = useState(false);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");

  const conversationsUrl = API_URL.replace(/\/chat\/?$/, "/conversations");

  const lastMeta = useMemo(
    () => [...messages].reverse().find(m => m.role === "assistant" && m.meta)?.meta,
    [messages]
  );

  useEffect(() => {
    loadConversations(true);
  }, []);

  async function loadConversations(openLatest = false) {
    setHistoryLoading(true);
    setHistoryError("");

    try {
      const response = await fetch(conversationsUrl);

      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }

      const data: ConversationSummary[] = await response.json();
      setConversations(data);

      if (openLatest && data.length > 0) {
        await openConversation(data[0]);
      }
    } catch (error) {
      const detail =
        error instanceof Error
          ? error.message
          : "Unknown error";

      setHistoryError(`Unable to load conversation history. ${detail}`);
    } finally {
      setHistoryLoading(false);
    }
  }

  function mapConversationMessages(
    storedMessages: ConversationMessage[]
  ): Message[] {
    return storedMessages.map((message) => ({
      id: message.id,
      role: message.role.toLowerCase() === "user"
        ? "user"
        : "assistant",
      text: message.message
    }));
  }

  async function openConversation(conversation: ConversationSummary) {
    if (busy) return;

    try {
      setHistoryError("");

      const response = await fetch(
        `${conversationsUrl}/${encodeURIComponent(conversation.sessionId)}`
      );

      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }

      const storedMessages: ConversationMessage[] =
        await response.json();

      setSessionId(conversation.sessionId);
      setMessages(mapConversationMessages(storedMessages));
      setInput("");
    } catch (error) {
      const detail =
        error instanceof Error
          ? error.message
          : "Unknown error";

      setHistoryError(`Unable to open conversation. ${detail}`);
    }
  }

  async function sendMessage(value = input) {
    const message = value.trim();
    if (!message || busy) return;

    setInput("");
    const userId = Date.now();
    setMessages(prev => [
      ...prev,
      { id: userId, role: "user", text: message },
      { id: userId + 1, role: "assistant", text: "", loading: true }
    ]);
    setBusy(true);

    try {
      const response = await fetch(API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId, message })
      });

      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }

      const data: ApiResponse = await response.json();
      setMessages(prev =>
        prev.map(m =>
          m.id === userId + 1
            ? { ...m, text: data.answer || "No answer returned.", meta: data, loading: false }
            : m
        )
      );

      await loadConversations(false);
    } catch (error) {
      const detail =
          error instanceof Error
              ? error.message
              : "Unknown error";

      setMessages(prev =>
          prev.map(m =>
              m.id === userId + 1
                  ? {
                    ...m,
                    text: `I couldn't connect to the Spring Boot API. ${detail}`,
                    meta: {
                      answer: `I couldn't connect to the Spring Boot API. ${detail}`,
                      type: "ERROR" as const,
                      source: "Backend",
                      success: false
                    },
                    loading: false
                  }
                  : m
          )
      );
    } finally {
      setBusy(false);
    }
  }

  function newChat() {
    if (busy) return;

    setMessages([]);
    setSessionId(`ui-${Date.now()}`);
    setInput("");
    setHistoryError("");
  }

  return (
    <div className={dark ? "app dark" : "app light"}>
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark"><Sparkles size={19} /></div>
          <div>
            <strong>Enterprise AI</strong>
            <span>Agentic Workspace</span>
          </div>
        </div>

        <button className="new-chat" onClick={newChat}>
          <Plus size={17} /> New conversation
        </button>

        <div className="side-label history-label">CONVERSATIONS</div>
        <div className="quick-list conversation-list">
          {historyLoading ? (
            <div className="empty-history">Loading conversations...</div>
          ) : conversations.length === 0 ? (
            <div className="empty-history">Your conversations will appear here.</div>
          ) : (
            conversations.map(conversation => (
              <button
                key={conversation.sessionId}
                className={conversation.sessionId === sessionId ? "active-conversation" : ""}
                onClick={() => openConversation(conversation)}
                title={conversation.title}
              >
                <span>{conversation.title}</span>
                <ChevronRight size={14} />
              </button>
            ))
          )}
        </div>

        {historyError && (
          <div className="history-error">{historyError}</div>
        )}

        <div className="side-label history-label">SESSION</div>
        <div className="session-card">
          <div className="online-dot" />
          <div>
            <span>Active session</span>
            <small>{sessionId}</small>
          </div>
        </div>

        <div className="sidebar-bottom">
          <button className="side-action"><Settings2 size={16} /> Configuration</button>
          <button className="side-action"><Zap size={16} /> Agent status <b>Online</b></button>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div className="mobile-brand"><Sparkles size={17} /> Enterprise AI</div>
          <div className="topbar-right">
            <span className="status"><i /> System online</span>
            <button className="icon-btn" onClick={() => setDark(v => !v)} title="Toggle theme">
              {dark ? <Sun size={17} /> : <Moon size={17} />}
            </button>
            <button className="icon-btn mobile-menu"><Menu size={18} /></button>
          </div>
        </header>

        <section className="workspace">
          <div className="chat-column">
            {messages.length === 0 ? (
              <div className="welcome">
                <div className="hero-orb"><Bot size={31} /></div>
                <div className="eyebrow"><span /> AGENTIC AI ASSISTANT</div>
                <h1>Ask. Orchestrate.<br /><em>Get answers.</em></h1>
                <p>
                  An enterprise assistant that intelligently routes each request
                  across your database, company knowledge, web search and LLM.
                </p>
                <div className="suggestions">
                  {examples.map(q => (
                    <button key={q} onClick={() => sendMessage(q)}>
                      <span>{q}</span><Send size={14} />
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="messages">
                {messages.map(m => (
                  <div key={m.id} className={`message-row ${m.role}`}>
                    {m.role === "assistant" && (
                      <div className="avatar agent-avatar"><Sparkles size={15} /></div>
                    )}
                    <div className={`bubble ${m.role}`}>
                      {m.loading ? (
                        <div className="thinking">
                          <span /><span /><span />
                          <label>Supervisor is orchestrating...</label>
                        </div>
                      ) : (
                        <>
                          <div className="answer">{parseAnswer(m.text)}</div>
                          {m.meta && (
                            <div className="response-meta">
                              <span className={m.meta.success === false ? "failed" : ""}>
                                {sourceIcon(m.meta.source)} {m.meta.source || "Assistant"}
                              </span>
                              <span>{m.meta.type || "GENERAL"}</span>
                              <span>{m.meta.success === false ? "Handled safely" : "Verified response"}</span>
                            </div>
                          )}
                        </>
                      )}
                    </div>
                    {m.role === "user" && <div className="avatar user-avatar">You</div>}
                  </div>
                ))}
              </div>
            )}

            <div className="composer-wrap">
              <div className="composer">
                <textarea
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      sendMessage();
                    }
                  }}
                  placeholder="Ask the enterprise assistant anything..."
                  rows={1}
                />
                <button className="send-btn" disabled={!input.trim() || busy} onClick={() => sendMessage()}>
                  <Send size={17} />
                </button>
              </div>
              <div className="composer-note">AI can make mistakes. Verify important information.</div>
            </div>
          </div>

          <aside className={`activity ${showActivity ? "" : "collapsed"}`}>
            <div className="activity-head">
              <div>
                <span className="panel-kicker">LIVE</span>
                <h2>Agent activity</h2>
              </div>
              <button className="icon-btn" onClick={() => setShowActivity(false)}><X size={16} /></button>
            </div>

            <div className="activity-intro">
              <div className="pulse-ring"><Bot size={18} /></div>
              <div><strong>Supervisor Agent</strong><span>{busy ? "Executing request" : "Ready for request"}</span></div>
            </div>

            <div className="pipeline">
              <Step label="Request analysis" icon={<Sparkles size={15} />} state={messages.length ? "done" : "idle"} />
              <div className="connector" />
              <Step label="Tool selection" icon={<Zap size={15} />} state={busy ? "active" : messages.length ? "done" : "idle"} />
              <div className="connector" />
              <Step label="Data retrieval" icon={<Database size={15} />} state={busy ? "active" : messages.length ? "done" : "idle"} />
              <div className="connector" />
              <Step label="Response synthesis" icon={<Bot size={15} />} state={busy ? "active" : messages.length ? "done" : "idle"} />
            </div>

            <div className="tool-section">
              <div className="section-title">AVAILABLE TOOLS</div>
              <Tool name="Employee Database" desc="Verified enterprise records" icon={<Database size={16} />} />
              <Tool name="Company Knowledge" desc="RAG · internal documents" icon={<FileText size={16} />} />
              <Tool name="Web Search" desc="Current external information" icon={<Globe2 size={16} />} />
              <Tool name="General LLM" desc="General reasoning & answers" icon={<Sparkles size={16} />} />
            </div>

            {lastMeta && (
              <div className="last-result">
                <div className="section-title">LAST RESPONSE</div>
                <div className="result-card">
                  <div className="result-icon">{sourceIcon(lastMeta.source)}</div>
                  <div><strong>{lastMeta.type || "GENERAL"}</strong><span>{lastMeta.source || "LLM"}</span></div>
                  <div className={lastMeta.success === false ? "result-status fail" : "result-status"}>●</div>
                </div>
              </div>
            )}
          </aside>

          {!showActivity && (
            <button className="reopen-panel" onClick={() => setShowActivity(true)}>
              <ChevronRight size={16} /> Agent
            </button>
          )}
        </section>
      </main>
    </div>
  );
}

function Step({ label, icon, state }: { label: string; icon: React.ReactNode; state: string }) {
  return (
    <div className={`step ${state}`}>
      <div className="step-icon">{icon}</div>
      <span>{label}</span>
      {state === "done" && <b>✓</b>}
      {state === "active" && <i />}
    </div>
  );
}

function Tool({ name, desc, icon }: { name: string; desc: string; icon: React.ReactNode }) {
  return (
    <div className="tool">
      <div className="tool-icon">{icon}</div>
      <div><strong>{name}</strong><span>{desc}</span></div>
      <i />
    </div>
  );
}

export default App;
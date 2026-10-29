import { useEffect, useMemo, useRef, useState } from "react";
import { AnswerContent } from "./AnswerContent";
import { IntelligenceOrb } from "./IntelligenceOrb";
import { ClickFeedback } from "./ClickFeedback";
import { checkLoginRateLimit } from "./loginRateLimit";
import { SessionCreation } from "./sessionCreation";
import {
  initializeKeycloak, keycloakMode, keycloakAuthorization, keycloakLogin,
  keycloakLogout, keycloakAccount, keycloakAdminUrl, validateAuthConfiguration
} from "./keycloakAuth";
import {
  Activity,
  Bot,
  ChevronRight,
  Database,
  FileText,
  Globe2,
  KeyRound,
  LogOut,
  Menu,
  MessageSquare,
  Moon,
  Plus,
  Send,
  Settings2,
  Sparkles,
  Sun,
  X,
  Zap
} from "lucide-react";

type ApiResponse = {
  answer: string;
  source?: string | null;
  type?: string | null;
  success?: boolean | null;
  data?: unknown;
};

type Message = {
  id: number;
  role: "user" | "assistant";
  text: string;
  meta?: ApiResponse;
  loading?: boolean;
};

type Credentials = {
  username: string;
  password: string;
};

type AccountProfile = {
  username: string;
  roles: string[];
  authMode?: "basic" | "keycloak";
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
  source?: string | null;
  type?: string | null;
  success?: boolean | null;
  createdAt: string;
};

type LatencySummary = {
  name: string;
  outcome: string;
  count: number;
  p95Ms: number;
  p99Ms: number;
  maxMs: number;
};

type LatencyReport = {
  windowSeconds: number;
  generatedAt: string;
  metrics: LatencySummary[];
};

type ChatLimits = {
  maxQuestions: number;
  maxMessageChars: number;
};

const LATENCY_REFRESH_MS = 10_000;

const STAGE_LABELS: Record<string, string> = {
  total_request: "Total request",
  request_guard: "Request safety checks",
  cache_lookup: "Cache lookup",
  load_context: "Load conversation",
  save_user: "Save question",
  process: "AI processing",
  classification: "AI classification",
  classification_fast_path: "Classification skipped",
  database_fast_path: "Direct employee lookup",
  classification_fallback: "Keyword fallback",
  tool_execution: "Tool execution + answer",
  database_query: "Employee database query",
  rag_retrieval: "Policy retrieval (embedding + vector search)",
  rag_generation: "Policy answer generation",
  save_assistant: "Save answer"
};

const STAGE_ORDER = Object.keys(STAGE_LABELS);

function formatMs(ms: number) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms.toFixed(1)} ms`;
}

function latencyLevel(ms: number) {
  if (ms >= 10_000) return "slow";
  if (ms >= 3_000) return "warn";
  return "ok";
}

const API_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8080/api/chat";

const examples = [
  "Who is employee 101?",
  "What is our annual leave policy?",
  "What is the latest information about Spring AI?",
  "Give me employee 101's role, the annual leave policy and the latest Spring AI information."
];

function sourceIcon(source = "") {
  const s = source.toLowerCase();

  if (s.includes("database")) {
    return <Database size={14} />;
  }

  if (s.includes("document") || s.includes("rag")) {
    return <FileText size={14} />;
  }

  if (s.includes("web")) {
    return <Globe2 size={14} />;
  }

  return <Sparkles size={14} />;
}

function basicAuthorization(credentials: Credentials) {
  const encodedBytes = new TextEncoder().encode(
    `${credentials.username}:${credentials.password}`
  );
  let binary = "";

  encodedBytes.forEach(byte => {
    binary += String.fromCharCode(byte);
  });

  return `Basic ${btoa(binary)}`;
}

function App() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [sessionId, setSessionId] = useState("");
  const sessionCreation = useRef<SessionCreation | null>(null);
  const newSessionPending = useRef(false);
  const [sessionCreating, setSessionCreating] = useState(false);

  const [dark, setDark] = useState(true);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [showActivity, setShowActivity] = useState(true);
  const [busy, setBusy] = useState(false);

  const [conversations, setConversations] = useState<
    ConversationSummary[]
  >([]);

  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [authenticated, setAuthenticated] = useState(false);
  const [authChecking, setAuthChecking] = useState(false);
  const [authError, setAuthError] = useState("");
  const [accountProfile, setAccountProfile] = useState<AccountProfile | null>(null);
  const [showUserForm, setShowUserForm] = useState(false);
  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [userFormError, setUserFormError] = useState("");
  const [userFormSuccess, setUserFormSuccess] = useState("");
  const [userCreating, setUserCreating] = useState(false);
  const userCreationPending = useRef(false);
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [replacementPassword, setReplacementPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordChanging, setPasswordChanging] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [authNotice, setAuthNotice] = useState("");
  const [showLatency, setShowLatency] = useState(false);
  const [latencyReport, setLatencyReport] = useState<LatencyReport | null>(null);
  const [latencyError, setLatencyError] = useState("");
  const [latencyLoading, setLatencyLoading] = useState(false);
  const [chatLimits, setChatLimits] = useState<ChatLimits | null>(null);
  const [composerError, setComposerError] = useState("");

  const conversationsUrl = API_URL.replace(
    /\/chat\/?$/,
    "/conversations"
  );
  const authProfileUrl = API_URL.replace(/\/chat\/?$/, "/auth/me");
  const adminUsersUrl = API_URL.replace(/\/chat\/?$/, "/admin/users");
  const passwordUrl = API_URL.replace(/\/chat\/?$/, "/auth/password");
  const latencyUrl = API_URL.replace(/\/chat\/?$/, "/admin/latency");
  const chatLimitsUrl = `${API_URL.replace(/\/$/, "")}/limits`;
  const isAdmin = accountProfile?.roles.includes("ADMIN") ?? false;

  useEffect(() => {
    let active = true;
    async function initializeIdentity() {
      try {
        validateAuthConfiguration();
        if (!keycloakMode) return;
        setAuthChecking(true);
        const signedIn = await initializeKeycloak();
        if (active && signedIn) await establishSession(null, () => active);
      } catch (error) {
        if (active) {
          signOut();
          setAuthError(error instanceof Error ? error.message : "Unable to initialize Keycloak.");
        }
      } finally {
        if (active) setAuthChecking(false);
      }
    }
    void initializeIdentity();
    return () => { active = false; };
    // Adapter initialization is shared across React StrictMode effect replays.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!mobileNavOpen) return;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobileNavOpen(false);
        document.querySelector<HTMLButtonElement>(".mobile-menu")?.focus();
      }
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [mobileNavOpen]);

  async function loadLatency() {
    setLatencyLoading(true);
    try {
      const response = await authenticatedFetch(latencyUrl);
      if (response.status === 403) {
        throw new Error("Only administrators can view latency.");
      }
      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }
      setLatencyReport(await response.json());
      setLatencyError("");
    } catch (error) {
      setLatencyError(
        error instanceof Error ? error.message : "Unable to load latency."
      );
    } finally {
      setLatencyLoading(false);
    }
  }

  useEffect(() => {
    if (!showLatency || !isAdmin) {
      return;
    }
    void loadLatency();
    const timer = window.setInterval(() => void loadLatency(), LATENCY_REFRESH_MS);
    return () => window.clearInterval(timer);
    // Credentials are captured on open; sign-out closes the panel.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showLatency, isAdmin]);

  const latencyRows = useMemo(
    () =>
      [...(latencyReport?.metrics ?? [])].sort((a, b) => {
        const order = (name: string) => {
          const index = STAGE_ORDER.indexOf(name);
          return index < 0 ? STAGE_ORDER.length : index;
        };
        return order(a.name) - order(b.name) || a.outcome.localeCompare(b.outcome);
      }),
    [latencyReport]
  );

  /*
   * Finds the latest assistant response that has metadata.
   * This is used by the Agent Activity panel.
   */
  const lastMeta = useMemo(
    () =>
      [...messages]
        .reverse()
        .find(
          message =>
            message.role === "assistant" &&
            message.meta
        )?.meta,
    [messages]
  );

  /*
   * Load all persisted conversations.
   */
  async function loadConversations(
    openLatest = false,
    authCredentials = credentials
  ) {
    setHistoryLoading(true);
    setHistoryError("");

    try {
      const response = await authenticatedFetch(
        conversationsUrl,
        {},
        authCredentials
      );

      if (!response.ok) {
        throw new Error(
          `API returned ${response.status}`
        );
      }

      const data: ConversationSummary[] =
        await response.json();

      setConversations(data);

      if (
        openLatest &&
        data.length > 0
      ) {
        await openConversation(data[0], authCredentials);
      }
    } catch (error) {
      const detail =
        error instanceof Error
          ? error.message
          : "Unknown error";

      setHistoryError(
        `Unable to load conversation history. ${detail}`
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  async function authenticatedFetch(
    url: string,
    options: RequestInit = {},
    authCredentials = credentials
  ) {
    if (!keycloakMode && !authCredentials) {
      throw new Error("Sign in to continue.");
    }

    const headers = new Headers(options.headers);
    try {
      headers.set("Authorization", await authorization(authCredentials));
    } catch (error) {
      if (keycloakMode) signOut();
      throw error;
    }

    const response = await fetch(url, { ...options, headers });
    checkLoginRateLimit(response);
    if (response.status === 401) {
      signOut();
      setAuthError("Your sign-in has expired. Please sign in again.");
      throw new Error("Your sign-in has expired. Please sign in again.");
    }

    return response;
  }

  async function signIn(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthError("");
    setAuthChecking(true);

    try {
      validateAuthConfiguration();
      if (keycloakMode) {
        await keycloakLogin();
        return;
      }
      await establishSession({ username, password });
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Unable to sign in.");
      setPassword("");
    } finally {
      setAuthChecking(false);
    }
  }

  async function authorization(authCredentials: Credentials | null) {
    if (keycloakMode) return keycloakAuthorization();
    if (!authCredentials) throw new Error("Sign in to continue.");
    return basicAuthorization(authCredentials);
  }

  async function establishSession(
    submittedCredentials: Credentials | null,
    isActive: () => boolean = () => true
  ) {
      const response = await fetch(conversationsUrl, {
        headers: {
          Authorization: await authorization(submittedCredentials)
        }
      });

      checkLoginRateLimit(response);
      if (response.status === 401) {
        throw new Error(keycloakMode
          ? "Keycloak sign-in was not accepted by the API. Check the issuer, audience and token validity."
          : "Invalid username or password.");
      }
      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }

      const profileResponse = await fetch(authProfileUrl, {
        headers: {
          Authorization: await authorization(submittedCredentials)
        }
      });
      checkLoginRateLimit(profileResponse);
      if (!profileResponse.ok) {
        throw new Error(`Unable to load account profile (${profileResponse.status}).`);
      }
      const profile: AccountProfile = await profileResponse.json();
      if ((profile.authMode === "keycloak") !== keycloakMode) {
        throw new Error("UI and backend authentication modes do not match. Check their configuration.");
      }

      const limitsResponse = await fetch(chatLimitsUrl, {
        headers: { Authorization: await authorization(submittedCredentials) }
      });
      checkLoginRateLimit(limitsResponse);
      if (!limitsResponse.ok) {
        throw new Error(`Unable to load chat limits (${limitsResponse.status}).`);
      }
      const limits: ChatLimits = await limitsResponse.json();
      const data: ConversationSummary[] = await response.json();
      if (!isActive()) return;
      setChatLimits(limits);
      setConversations(data);
      setHistoryLoading(false);
      setCredentials(submittedCredentials);
      setAccountProfile(profile);
      setAuthenticated(true);
      setPassword("");

      if (data.length > 0) {
        await openConversation(data[0], submittedCredentials);
      }
  }

  /*
   * Convert backend conversation messages
   * into the frontend Message structure.
   *
   * IMPORTANT:
   * source/type/success are preserved here.
   * This is what allows reopened conversations
   * to display their original source.
   */
  function mapConversationMessages(
    storedMessages: ConversationMessage[]
  ): Message[] {
    return storedMessages.map(message => {
      const isUser =
        message.role.toLowerCase() === "user";

      const isAssistant =
        message.role.toLowerCase() === "assistant";

      const hasMetadata =
        isAssistant &&
        !!message.type;

      return {
        id: message.id,

        role: isUser
          ? "user"
          : "assistant",

        text: message.message,

        meta: hasMetadata
          ? {
              answer: message.message,
              source: message.source ?? "Assistant",
              type: message.type ?? "GENERAL",
              success:
                message.success ?? false
            }
          : undefined
      };
    });
  }

  /*
   * Open a previously stored conversation.
   */
  async function openConversation(
    conversation: ConversationSummary,
    authCredentials = credentials
  ) {
    if (busy || newSessionPending.current) {
      return;
    }

    try {
      setHistoryError("");

      const response = await authenticatedFetch(
        `${conversationsUrl}/${encodeURIComponent(conversation.sessionId)}`,
        {},
        authCredentials
      );

      if (!response.ok) {
        throw new Error(
          `API returned ${response.status}`
        );
      }

      const storedMessages: ConversationMessage[] =
        await response.json();

      setSessionId(
        conversation.sessionId
      );
      sessionCreation.current = null;

      setMessages(
        mapConversationMessages(
          storedMessages
        )
      );

      setInput("");
      setComposerError("");
    } catch (error) {
      const detail =
        error instanceof Error
          ? error.message
          : "Unknown error";

      setHistoryError(
        `Unable to open conversation. ${detail}`
      );
    }
  }

  /*
   * Send a new message.
   */
  async function sendMessage(
    value = input
  ) {
    const message = value.trim();

    if (!message || busy || newSessionPending.current) {
      return;
    }
    if (!chatLimits || value.length > chatLimits.maxMessageChars) {
      setComposerError(chatLimits
        ? `Use at most ${chatLimits.maxMessageChars} characters per question.`
        : "Chat limits are not loaded. Sign in again.");
      return;
    }

    setComposerError("");
    setInput("");

    const userId = Date.now();
    const assistantId = userId + 1;

    /*
     * Immediately show the user's message
     * and loading state.
     */
    setMessages(previous => [
      ...previous,
      {
        id: userId,
        role: "user",
        text: message
      },
      {
        id: assistantId,
        role: "assistant",
        text: "",
        loading: true
      }
    ]);

    setBusy(true);

    try {
      const activeSessionId = sessionId || await createSession();
      const response = await authenticatedFetch(
        API_URL,
        {
          method: "POST",
          headers: {
            "Content-Type":
              "application/json"
          },
          body: JSON.stringify({
            sessionId: activeSessionId,
            message
          })
        },
        credentials
      );

      if (!response.ok) {
        const detail: ApiResponse | null = response.headers.get("content-type")?.includes("application/json")
          ? await response.json()
          : null;
        throw new Error(
          detail?.answer || `API returned ${response.status}`
        );
      }

      const data: ApiResponse =
        await response.json();

      /*
       * Update the live assistant message
       * with answer + source + type + success.
       */
      setMessages(previous =>
        previous.map(item =>
          item.id === assistantId
            ? {
                ...item,
                text:
                  data.answer ||
                  "No answer returned.",
                meta: data,
                loading: false
              }
            : item
        )
      );

      /*
       * Refresh conversation list so
       * the new conversation appears.
       */
      await loadConversations(false);
    } catch (error) {
      const detail =
        error instanceof Error
          ? error.message
          : "Unknown error";

      const errorMessage =
        `The message could not be processed. ${detail}`;

      setMessages(previous =>
        previous.map(item =>
          item.id === assistantId
            ? {
                ...item,

                text: errorMessage,

                meta: {
                  answer: errorMessage,
                  type: "ERROR",
                  source: "Backend",
                  success: false
                },

                loading: false
              }
            : item
        )
      );
    } finally {
      setBusy(false);
    }
  }

  /*
   * Start a completely new conversation.
   */
  async function createSession() {
    const creation = sessionCreation.current ?? new SessionCreation();
    sessionCreation.current = creation;
    const id = await creation.create(async creationKey => {
      const response = await authenticatedFetch(conversationsUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ creationKey })
      });
      if (!response.ok) {
        const detail: ApiResponse | null = response.headers.get("content-type")?.includes("application/json")
          ? await response.json() : null;
        throw new Error(detail?.answer || `Unable to create conversation (${response.status}).`);
      }
      const data: { sessionId: string } = await response.json();
      return data.sessionId;
    });
    if (sessionCreation.current !== creation) {
      throw new Error("Session creation was cancelled because the active account or conversation changed.");
    }
    setSessionId(id);
    return id;
  }

  async function newChat() {
    if (busy || newSessionPending.current) {
      return;
    }
    newSessionPending.current = true;
    setSessionCreating(true);
    setComposerError("");

    if (sessionId || messages.length > 0 || sessionCreation.current === null) {
      sessionCreation.current = new SessionCreation();
    }
    setMessages([]);
    setSessionId("");
    setInput("");
    setHistoryError("");
    try {
      await createSession();
    } catch (error) {
      setComposerError(error instanceof Error ? error.message : "Unable to create conversation.");
    } finally {
      newSessionPending.current = false;
      setSessionCreating(false);
    }
  }

  function signOut() {
    setMobileNavOpen(false);
    setCredentials(null);
    setAuthenticated(false);
    setConversations([]);
    setMessages([]);
    setInput("");
    sessionCreation.current = null;
    setSessionId("");
    setAuthError("");
    setAccountProfile(null);
    setShowUserForm(false);
    setNewUsername("");
    setNewPassword("");
    closePasswordForm();
    setAuthNotice("");
    setShowLatency(false);
    setLatencyReport(null);
    setLatencyError("");
    setChatLimits(null);
    setComposerError("");
  }

  async function endSession() {
    signOut();
    if (keycloakMode) {
      try {
        await keycloakLogout();
      } catch (error) {
        setAuthError(error instanceof Error ? error.message : "Unable to sign out of Keycloak.");
      }
    }
  }

  async function manageIdentityAccount() {
    try {
      await keycloakAccount();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Unable to open Keycloak account management.");
    }
  }

  function closePasswordForm() {
    setShowPasswordForm(false);
    setCurrentPassword("");
    setReplacementPassword("");
    setConfirmPassword("");
    setPasswordError("");
  }

  async function changePassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (passwordChanging) {
      return;
    }
    setPasswordError("");
    if (replacementPassword !== confirmPassword) {
      setPasswordError("New passwords do not match.");
      return;
    }
    if (new TextEncoder().encode(replacementPassword).length > 72) {
      setPasswordError("New password must be no more than 72 UTF-8 bytes.");
      return;
    }

    setPasswordChanging(true);
    try {
      const response = await authenticatedFetch(passwordUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          currentPassword,
          newPassword: replacementPassword
        })
      });
      if (!response.ok) {
        const error: ApiResponse = await response.json();
        throw new Error(error.answer || `API returned ${response.status}`);
      }
      signOut();
      setAuthNotice("Password changed. Sign in with your new password.");
    } catch (error) {
      setPasswordError(
        error instanceof Error ? error.message : "Unable to change password."
      );
    } finally {
      setPasswordChanging(false);
    }
  }

  async function createUser(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (userCreationPending.current) {
      return;
    }
    setUserFormError("");
    setUserFormSuccess("");

    if (new TextEncoder().encode(newPassword).length > 72) {
      setUserFormError("Password must be no more than 72 UTF-8 bytes.");
      return;
    }

    userCreationPending.current = true;
    setUserCreating(true);
    try {
      const response = await authenticatedFetch(adminUsersUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: newUsername,
          password: newPassword
        })
      });

      if (response.status === 409) {
        throw new Error("That username already exists.");
      }
      if (response.status === 403) {
        throw new Error("Only administrators can create accounts.");
      }
      if (!response.ok) {
        const detail: ApiResponse = await response.json();
        throw new Error(detail.answer || `API returned ${response.status}`);
      }

      const created: { username: string } = await response.json();
      setUserFormSuccess(`Created account ${created.username}.`);
      setNewUsername("");
      setNewPassword("");
    } catch (error) {
      setUserFormError(
        error instanceof Error ? error.message : "Unable to create account."
      );
    } finally {
      userCreationPending.current = false;
      setUserCreating(false);
    }
  }

  if (!authenticated) {
    return (
      <ClickFeedback><div className="app auth-screen">
        <div className="auth-ambient" aria-hidden="true">
          <span className="auth-glow auth-glow-violet" />
          <span className="auth-glow auth-glow-teal" />
          <div className="auth-grid" />
        </div>
        <div className="auth-layout">
          <section className="auth-intro" aria-labelledby="auth-intro-title">
            <div className="auth-brand">
              <div className="brand-mark"><Sparkles size={22} aria-hidden="true" /></div>
              <span>ENTERPRISE AI <small>YOUR AGENTIC WORKSPACE</small></span>
            </div>
            <div className="auth-pill"><Sparkles size={13} /> Intelligence, beautifully connected.</div>
            <h1 id="auth-intro-title">Your next big idea<br /> <span>starts with a question.</span></h1>
            <p className="auth-intro-copy">
              One conversation. A world of context. Meet the workspace
              that connects your people, policies and possibilities.
            </p>
            <div className="auth-visual">
              <IntelligenceOrb />
              <span className="orb-label orb-label-people"><Database size={15} /> People & insights</span>
              <span className="orb-label orb-label-policy"><FileText size={15} /> Company knowledge</span>
              <span className="orb-label orb-label-web"><Globe2 size={15} /> Beyond the workspace</span>
            </div>
          </section>
        <form className="auth-card auth-login-card" onSubmit={signIn} aria-labelledby="auth-signin-title">
          <div className="hero-orb">
            <KeyRound size={26} aria-hidden="true" />
          </div>
          <div className="auth-card-kicker">LET'S MAKE THINGS HAPPEN</div>
          <h2 id="auth-signin-title">Welcome back.</h2>
          <p>Sign in and pick up the conversation.</p>

          {!keycloakMode && <><label htmlFor="signin-username">
            Username
            <input
              id="signin-username"
              name="username"
              placeholder="Enter your username"
              autoComplete="username"
              value={username}
              onChange={event => setUsername(event.target.value)}
              required
            />
          </label>
          <label htmlFor="signin-password">
            Password
            <input
              type="password"
              id="signin-password"
              name="password"
              placeholder="Enter your password"
              autoComplete="current-password"
              value={password}
              onChange={event => setPassword(event.target.value)}
              required
            />
          </label></>}
          {authError && <div className="auth-error" role="alert">{authError}</div>}
          {authNotice && <div className="account-success" role="status">{authNotice}</div>}
          <button className="auth-submit" type="submit" disabled={authChecking} aria-busy={authChecking}>
            <span>{authChecking ? "Signing in..." : keycloakMode ? "Sign in with Keycloak" : "Sign in"}</span>
            {authChecking ? <span className="auth-spinner" aria-hidden="true" /> : <ChevronRight size={18} aria-hidden="true" />}
          </button>
          <small>{keycloakMode
            ? "Keycloak handles your password. Access and refresh tokens stay in memory only."
            : "Credentials are held only until you sign out or close this page."}</small>
          <div className="auth-access-note">Need access? Contact your administrator.</div>
        </form>
        </div>
        <footer className="auth-footer">Designed for curiosity. Built for your enterprise.</footer>
      </div></ClickFeedback>
    );
  }

  return (
    <ClickFeedback><div
      className={
        dark
          ? "app workspace-app dark"
          : "app workspace-app light"
      }
    >
      {mobileNavOpen && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)} />}
      <aside className={`sidebar${mobileNavOpen ? " sidebar-open" : ""}`} id="workspace-navigation">
        <button className="icon-btn nav-close" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)}><X size={18} /></button>

        <div className="brand">
          <div className="brand-mark">
            <Sparkles size={19} />
          </div>

          <div>
            <strong>
              Enterprise AI
            </strong>

            <span>
              Agentic Workspace
            </span>
          </div>
        </div>

        <button
          className="new-chat"
          onClick={() => { setMobileNavOpen(false); void newChat(); }}
          disabled={busy || sessionCreating}
        >
          <Plus size={17} />
          New conversation
        </button>

        <div className="side-label history-label">
          CONVERSATIONS
        </div>

        <div className="quick-list conversation-list">

          {historyLoading ? (
            <div className="empty-history">
              Loading conversations...
            </div>
          ) : conversations.length === 0 ? (
            <div className="empty-history">
              Your conversations will appear here.
            </div>
          ) : (
            conversations.map(
              conversation => (
                <button
                  key={
                    conversation.sessionId
                  }
                  className={
                    conversation.sessionId ===
                    sessionId
                      ? "conversation-item active-conversation"
                      : "conversation-item"
                  }
                  aria-current={conversation.sessionId === sessionId ? "true" : undefined}
                  onClick={() => {
                    setMobileNavOpen(false);
                    void openConversation(conversation);
                  }}
                  title={
                    conversation.title
                  }
                >
                  <MessageSquare size={16} className="conversation-item-icon" aria-hidden="true" />
                  <span className="conversation-item-title">
                    {
                      conversation.title
                    }
                  </span>

                  <ChevronRight
                    size={14}
                    className="conversation-item-arrow"
                    aria-hidden="true"
                  />
                </button>
              )
            )
          )}

        </div>

        {historyError && (
          <div className="history-error">
            {historyError}
          </div>
        )}

        <div className="side-label history-label">
          SESSION
        </div>

        <div className="session-card">
          <div className="online-dot" />

          <div>
            <span>
              Active session
            </span>

            <small>
              {sessionId || "New conversation"}
            </small>
          </div>
        </div>

        <div className="sidebar-bottom">

          {accountProfile?.roles.includes("ADMIN") && (
            <button
              className="side-action"
              onClick={() => {
                if (keycloakMode) {
                  window.open(keycloakAdminUrl(), "_blank", "noopener,noreferrer");
                  return;
                }
                setShowUserForm(value => !value);
                setUserFormError("");
                setUserFormSuccess("");
              }}
            >
              <Settings2 size={16} />
              {keycloakMode ? "Manage accounts in Keycloak" : "Manage accounts"}
            </button>
          )}

          {isAdmin && (
            <button
              className="side-action"
              onClick={() => setShowLatency(true)}
            >
              <Activity size={16} />
              Latency (P95 / P99)
            </button>
          )}

          <button className="side-action">
            <Zap size={16} />
            Agent status
            <b>Online</b>
          </button>

        </div>

        {showUserForm && !keycloakMode && accountProfile?.roles.includes("ADMIN") && (
          <form className="account-form" onSubmit={createUser}>
            <strong>Create user account</strong>
            <input
              aria-label="New username"
              placeholder="Username"
              autoComplete="off"
              minLength={3}
              maxLength={100}
              pattern="[A-Za-z0-9._@-]+"
              value={newUsername}
              disabled={userCreating}
              onChange={event => setNewUsername(event.target.value)}
              required
            />
            <input
              aria-label="Temporary password"
              placeholder="Temporary password (12+ chars)"
              type="password"
              autoComplete="new-password"
              minLength={12}
              maxLength={72}
              value={newPassword}
              disabled={userCreating}
              onChange={event => setNewPassword(event.target.value)}
              required
            />
            {userFormError && <small className="account-error" role="alert">{userFormError}</small>}
            {userFormSuccess && <small className="account-success" role="status">{userFormSuccess}</small>}
            <button className="auth-submit" type="submit" disabled={userCreating}>
              {userCreating ? "Creating account..." : "Create account"}
            </button>
          </form>
        )}

      </aside>

      <main className="main">

        <header className="topbar">
          <div className="workspace-heading"><span>Workspace</span><ChevronRight size={13} /><strong>AI assistant</strong></div>

          <div className="mobile-brand">
            <Sparkles size={17} />
            Enterprise AI
          </div>

          <div className="topbar-right">

            <button
              className="icon-btn"
              onClick={() => keycloakMode ? void manageIdentityAccount() : setShowPasswordForm(true)}
              disabled={busy || authChecking}
              title={keycloakMode ? "Manage Keycloak account" : "Change password"}
              aria-label={keycloakMode ? "Manage Keycloak account" : "Change password"}
            >
              <KeyRound size={16} />
            </button>

            <span className="status">
              <i />
              System online
            </span>

            <button
              className="icon-btn"
              onClick={() =>
                setDark(value => !value)
              }
              title="Toggle theme"
            >
              {dark ? (
                <Sun size={17} />
              ) : (
                <Moon size={17} />
              )}
            </button>

            <button
              className="icon-btn"
              onClick={() => void endSession()}
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={16} />
            </button>

            <button
              className="icon-btn mobile-menu"
              aria-label="Open navigation"
              aria-expanded={mobileNavOpen}
              aria-controls="workspace-navigation"
              onClick={() => setMobileNavOpen(value => !value)}
            >
              <Menu size={18} />
            </button>

          </div>
        </header>

        {showLatency && isAdmin && (
          <div className="account-overlay" onClick={() => setShowLatency(false)}>
            <section
              className="auth-card latency-card"
              role="dialog"
              aria-modal="true"
              aria-labelledby="latency-title"
              onClick={event => event.stopPropagation()}
            >
              <div className="latency-header">
                <h2 id="latency-title">Latency — P95 / P99</h2>
                <button
                  className="icon-btn"
                  type="button"
                  onClick={() => setShowLatency(false)}
                  aria-label="Close latency panel"
                >
                  <X size={16} />
                </button>
              </div>
              <small className="latency-help">
                P95: 95% of requests finished within this time. P99: 99% did.
                Server-side time for requests completed in the last{" "}
                {Math.round((latencyReport?.windowSeconds ?? 300) / 60)} minutes.
                Refreshes every {LATENCY_REFRESH_MS / 1000} seconds.
              </small>

              {latencyError && <div className="auth-error" role="alert">{latencyError}</div>}

              {!latencyReport && !latencyError && <small>Loading latency…</small>}

              {latencyReport && latencyRows.length === 0 && (
                <small className="latency-empty">
                  No chat requests finished in this window. Send a few questions, then check again.
                </small>
              )}

              {latencyRows.length > 0 && (
                <div className="latency-table-wrap">
                  <table className="latency-table">
                    <thead>
                      <tr>
                        <th>Stage</th>
                        <th>Outcome</th>
                        <th>Count</th>
                        <th>P95</th>
                        <th>P99</th>
                        <th>Max</th>
                      </tr>
                    </thead>
                    <tbody>
                      {latencyRows.map(row => (
                        <tr
                          key={`${row.name}-${row.outcome}`}
                          className={row.name === "total_request" ? "latency-total" : undefined}
                        >
                          <td>{STAGE_LABELS[row.name] ?? row.name}</td>
                          <td>{row.outcome}</td>
                          <td>{row.count}</td>
                          <td className={`latency-${latencyLevel(row.p95Ms)}`}>{formatMs(row.p95Ms)}</td>
                          <td className={`latency-${latencyLevel(row.p99Ms)}`}>{formatMs(row.p99Ms)}</td>
                          <td>{formatMs(row.maxMs)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {latencyRows.some(row => row.count < 100) && (
                <small className="latency-help">
                  Fewer than 100 samples: P99 equals the slowest request, so treat it as indicative only.
                </small>
              )}

              <div className="latency-footer">
                <small>
                  {latencyReport
                    ? `Updated ${new Date(latencyReport.generatedAt).toLocaleTimeString()}`
                    : ""}
                </small>
                <button
                  className="auth-submit"
                  type="button"
                  onClick={() => void loadLatency()}
                  disabled={latencyLoading}
                >
                  {latencyLoading ? "Refreshing…" : "Refresh now"}
                </button>
              </div>
            </section>
          </div>
        )}

        {authError && <div className="auth-error" role="alert">{authError}</div>}
        {showPasswordForm && !keycloakMode && (
          <div className="account-overlay">
            <form
              className="auth-card"
              role="dialog"
              aria-modal="true"
              aria-labelledby="password-title"
              onSubmit={changePassword}
            >
              <h2 id="password-title">Change password</h2>
              <label>
                Current password
                <input
                  autoFocus
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={event => setCurrentPassword(event.target.value)}
                  disabled={passwordChanging}
                  required
                />
              </label>
              <label>
                New password
                <input
                  type="password"
                  autoComplete="new-password"
                  minLength={12}
                  maxLength={72}
                  value={replacementPassword}
                  onChange={event => setReplacementPassword(event.target.value)}
                  disabled={passwordChanging}
                  required
                />
              </label>
              <label>
                Confirm new password
                <input
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={event => setConfirmPassword(event.target.value)}
                  disabled={passwordChanging}
                  required
                />
              </label>
              <small>Use at least 12 characters, up to 72 UTF-8 bytes. You will need to sign in again.</small>
              {passwordError && <div className="auth-error" role="alert">{passwordError}</div>}
              <button className="auth-submit" type="submit" disabled={passwordChanging}>
                {passwordChanging ? "Changing password..." : "Change password"}
              </button>
              <button className="auth-submit" type="button" onClick={closePasswordForm} disabled={passwordChanging}>
                Cancel
              </button>
            </form>
          </div>
        )}

        <section className="workspace">

          <div className="chat-column">

            {messages.length === 0 ? (

              <div className="welcome">

                <IntelligenceOrb compact />

                <div className="eyebrow">
                  <span />
                  YOUR IDEAS, AMPLIFIED
                </div>

                <h1>
                  Big questions.
                  <br />
                  <em>
                    Brilliantly connected.
                  </em>
                </h1>

                <p>
                  From company knowledge to a wider perspective.
                  What would you like to explore?
                </p>

                <div className="suggestions">

                  {examples.map(
                    (question, index) => (
                      <button
                        key={question}
                        onClick={() =>
                          sendMessage(
                            question
                          )
                        }
                      >
                        <span className={`suggestion-icon suggestion-icon-${index}`}>
                          {index === 0 ? <Database size={19} /> : index === 1 ? <FileText size={19} /> : index === 2 ? <Globe2 size={19} /> : <Sparkles size={19} />}
                        </span>
                        <span className="suggestion-copy">
                          <strong>{["Explore your people", "Know your policies", "Discover what's new", "Think a little bigger"][index]}</strong>
                          <span>{question}</span>
                        </span>
                        <ChevronRight size={15} />
                      </button>
                    )
                  )}

                </div>

              </div>

            ) : (

              <div className="messages">

                {messages.map(message => (

                  <div
                    key={message.id}
                    className={`message-row ${message.role}`}
                  >

                    {message.role ===
                      "assistant" && (
                      <div className="avatar agent-avatar">
                        <Sparkles
                          size={15}
                        />
                      </div>
                    )}

                    <div
                      className={`bubble ${message.role}`}
                    >

                      {message.loading ? (

                        <div className="thinking">
                          <span />
                          <span />
                          <span />

                          <label>
                            Supervisor is orchestrating...
                          </label>
                        </div>

                      ) : (

                        <>
                          <div className="answer">
                            {message.role === "assistant"
                              ? <AnswerContent text={message.text} data={message.meta?.data} />
                              : <p style={{ whiteSpace: "pre-wrap" }}>{message.text}</p>}
                          </div>

                          {message.meta && (
                            <div className="response-meta">

                              <span
                                className={
                                  message.meta
                                    .success ===
                                  false
                                    ? "failed"
                                    : ""
                                }
                              >
                                {sourceIcon(
                                  message.meta
                                    .source ??
                                    ""
                                )}

                                {message.meta
                                  .source ||
                                  "Assistant"}
                              </span>

                              <span>
                                {message.meta
                                  .type ||
                                  "GENERAL"}
                              </span>

                              <span>
                                {message.meta
                                  .success ===
                                false
                                  ? "Handled safely"
                                  : "Verified response"}
                              </span>

                            </div>
                          )}

                        </>

                      )}

                    </div>

                    {message.role ===
                      "user" && (
                      <div className="avatar user-avatar">
                        You
                      </div>
                    )}

                  </div>

                ))}

              </div>

            )}

            <div className="composer-wrap">

              <div className="composer">

                <textarea
                  value={input}
                  maxLength={chatLimits?.maxMessageChars}
                  onChange={event =>
                    setInput(
                      event.target.value
                    )
                  }
                  onKeyDown={event => {
                    if (
                      event.key ===
                        "Enter" &&
                      !event.shiftKey
                    ) {
                      event.preventDefault();
                      sendMessage();
                    }
                  }}
                  placeholder="Ask the enterprise assistant anything..."
                  aria-label="Your question"
                  rows={1}
                />

                <button
                  className="send-btn"
                  aria-label="Send message"
                  disabled={
                    !input.trim() ||
                    busy
                  }
                  onClick={() =>
                    sendMessage()
                  }
                >
                  <Send size={17} />
                </button>

              </div>

              <div className="composer-note">
                AI can make mistakes.
                Verify important
                information.
                {chatLimits && (
                  <> {input.length}/{chatLimits.maxMessageChars} characters.
                    {" "}{chatLimits.maxQuestions} questions per chat.</>
                )}
              </div>
              {composerError && <div className="auth-error" role="alert">{composerError}</div>}

            </div>

          </div>

          <aside
            className={`activity ${
              showActivity
                ? ""
                : "collapsed"
            }`}
          >

            <div className="activity-head">

              <div>
                <span className="panel-kicker">
                  LIVE
                </span>

                <h2>
                  Agent activity
                </h2>
              </div>

              <button
                className="icon-btn"
                onClick={() =>
                  setShowActivity(
                    false
                  )
                }
              >
                <X size={16} />
              </button>

            </div>

            <div className="activity-intro">

              <div className="pulse-ring">
                <Bot size={18} />
              </div>

              <div>
                <strong>
                  Supervisor Agent
                </strong>

                <span>
                  {busy
                    ? "Executing request"
                    : "Ready for request"}
                </span>
              </div>

            </div>

            <div className="pipeline">

              <Step
                label="Request analysis"
                icon={
                  <Sparkles size={15} />
                }
                state={
                  messages.length
                    ? "done"
                    : "idle"
                }
              />

              <div className="connector" />

              <Step
                label="Tool selection"
                icon={
                  <Zap size={15} />
                }
                state={
                  busy
                    ? "active"
                    : messages.length
                      ? "done"
                      : "idle"
                }
              />

              <div className="connector" />

              <Step
                label="Data retrieval"
                icon={
                  <Database size={15} />
                }
                state={
                  busy
                    ? "active"
                    : messages.length
                      ? "done"
                      : "idle"
                }
              />

              <div className="connector" />

              <Step
                label="Response synthesis"
                icon={
                  <Bot size={15} />
                }
                state={
                  busy
                    ? "active"
                    : messages.length
                      ? "done"
                      : "idle"
                }
              />

            </div>

            <div className="tool-section">

              <div className="section-title">
                AVAILABLE TOOLS
              </div>

              <Tool
                name="Employee Database"
                desc="Verified enterprise records"
                icon={
                  <Database size={16} />
                }
              />

              <Tool
                name="Company Knowledge"
                desc="RAG · internal documents"
                icon={
                  <FileText size={16} />
                }
              />

              <Tool
                name="Web Search"
                desc="Current external information"
                icon={
                  <Globe2 size={16} />
                }
              />

              <Tool
                name="General LLM"
                desc="General reasoning & answers"
                icon={
                  <Sparkles size={16} />
                }
              />

            </div>

            {lastMeta && (
              <div className="last-result">

                <div className="section-title">
                  LAST RESPONSE
                </div>

                <div className="result-card">

                  <div className="result-icon">
                    {sourceIcon(
                      lastMeta.source ??
                        ""
                    )}
                  </div>

                  <div>
                    <strong>
                      {lastMeta.type ||
                        "GENERAL"}
                    </strong>

                    <span>
                      {lastMeta.source ||
                        "LLM"}
                    </span>
                  </div>

                  <div
                    className={
                      lastMeta.success ===
                      false
                        ? "result-status fail"
                        : "result-status"
                    }
                  >
                    ●
                  </div>

                </div>

              </div>
            )}

          </aside>

          {!showActivity && (
            <button
              className="reopen-panel"
              onClick={() =>
                setShowActivity(true)
              }
            >
              <ChevronRight size={16} />
              Agent
            </button>
          )}

        </section>
      </main>
    </div></ClickFeedback>
  );
}

function Step({
  label,
  icon,
  state
}: {
  label: string;
  icon: React.ReactNode;
  state: string;
}) {
  return (
    <div className={`step ${state}`}>

      <div className="step-icon">
        {icon}
      </div>

      <span>
        {label}
      </span>

      {state === "done" && (
        <b>✓</b>
      )}

      {state === "active" && (
        <i />
      )}

    </div>
  );
}

function Tool({
  name,
  desc,
  icon
}: {
  name: string;
  desc: string;
  icon: React.ReactNode;
}) {
  return (
    <div className="tool">

      <div className="tool-icon">
        {icon}
      </div>

      <div>
        <strong>
          {name}
        </strong>

        <span>
          {desc}
        </span>
      </div>

      <i />

    </div>
  );
}

export default App;
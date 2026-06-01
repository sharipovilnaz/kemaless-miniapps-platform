import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowRight,
  ArrowLeftRight,
  Building2,
  CreditCard,
  FileText,
  History,
  Home,
  KeyRound,
  LogOut,
  Plus,
  PlugZap,
  Save,
  ShieldCheck,
  Smartphone,
  UserRound,
  X
} from "lucide-react";
import { api, authStore, isAuthError } from "./services/api";
import { MiniappContainer } from "./components/MiniappContainer";
import "./styles.css";

class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <main className="viewer-state full-page">
          <section className="boot-error">
            <strong>Сбой интерфейса</strong>
            <span>{this.state.error.message || String(this.state.error)}</span>
            <button type="button" onClick={() => window.location.reload()}>
              Обновить страницу
            </button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}

const moneyFormatter = new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB" });

function formatMoney(amount, currency = "RUB") {
  return new Intl.NumberFormat("ru-RU", { style: "currency", currency }).format(Number(amount || 0));
}

function accountStatusLabel(status) {
  if (status === "ACTIVE") {
    return "Активен";
  }
  if (status === "BLOCKED") {
    return "Заблокирован";
  }
  return "Закрыт";
}

function PublicHome({ onAuth }) {
  return (
    <main className="public-page">
      <header className="public-header">
        <strong>Kemaless Bank</strong>
        <button type="button" onClick={() => onAuth("login")}>Войти</button>
      </header>

      <section className="bank-hero">
        <div>
          <p className="eyebrow">Онлайн-банк</p>
          <h1>Kemaless Bank</h1>
          <p>Открывайте счета, управляйте профилем и подключайте платежные сервисы в защищенном кабинете.</p>
          <div className="hero-actions">
            <button className="icon-button" type="button" onClick={() => onAuth("register")}>
              Стать клиентом <ArrowRight size={18} />
            </button>
            <button className="secondary" type="button" onClick={() => onAuth("login")}>Войти в банк</button>
          </div>
        </div>
      </section>

      <section className="public-features">
        <article>
          <Building2 size={24} />
          <strong>Счета</strong>
          <span>Открытие и закрытие счетов онлайн.</span>
        </article>
        <article>
          <ShieldCheck size={24} />
          <strong>Безопасность</strong>
          <span>Современная авторизация и контроль входов с устройств.</span>
        </article>
        <article>
          <PlugZap size={24} />
          <strong>Сервисы</strong>
          <span>Подключение дополнительных приложений из каталога банка.</span>
        </article>
      </section>
    </main>
  );
}

function AuthPage({ initialMode = "login", onBack, onLogin }) {
  const [mode, setMode] = useState(initialMode);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setError("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      const response = mode === "login"
        ? await api.login({ username: form.get("username"), password: form.get("password") })
        : await api.register({
            username: form.get("username"),
            password: form.get("password"),
            fullName: form.get("fullName"),
            email: form.get("email"),
            phone: form.get("phone")
          });
      authStore.setSession(response);
      onLogin(response.user);
    } catch (error) {
      setError(error.message || (mode === "login" ? "Неверный логин или пароль" : "Не удалось создать аккаунт"));
    }
  }

  return (
    <main className="auth-page">
      <header className="public-header">
        <strong>Kemaless Bank</strong>
        <button className="secondary" type="button" onClick={onBack}>На главную</button>
      </header>

      <section className="auth-layout">
        <div>
          <p className="eyebrow">Личный кабинет</p>
          <h1>{mode === "login" ? "Вход в банк" : "Регистрация клиента"}</h1>
          <p>После входа откроется главная страница интернет-банка. Профиль и безопасность доступны отдельными разделами.</p>
        </div>

        <section className="auth-card">
          <div className="tabs">
            <button className={mode === "login" ? "active" : ""} type="button" onClick={() => setMode("login")}>Вход</button>
            <button className={mode === "register" ? "active" : ""} type="button" onClick={() => setMode("register")}>Регистрация</button>
          </div>

          <form className="form" onSubmit={submit}>
            {mode === "register" && (
              <>
                <label>ФИО<input name="fullName" required placeholder="Иван Петров" /></label>
                <label>Email<input name="email" required type="email" placeholder="ivan@example.com" /></label>
                <label>Телефон<input name="phone" placeholder="+7 900 000-00-00" /></label>
              </>
            )}
            <label>Логин<input name="username" required autoComplete="username" /></label>
            <label>
              Пароль
              <input
                name="password"
                required
                minLength={mode === "register" ? 8 : undefined}
                type="password"
                autoComplete={mode === "login" ? "current-password" : "new-password"}
              />
            </label>
            <button type="submit">{mode === "login" ? "Войти" : "Создать аккаунт"}</button>
            {error && <strong className="error">{error}</strong>}
          </form>
        </section>
      </section>
    </main>
  );
}

function BankHomePage({ user, accounts, onNavigate }) {
  const activeAccounts = accounts.filter((account) => account.status === "ACTIVE");
  const balancesByCurrency = useMemo(
    () => activeAccounts.reduce((totals, account) => {
      const currency = account.currency || "RUB";
      totals[currency] = (totals[currency] || 0) + Number(account.balance || 0);
      return totals;
    }, {}),
    [activeAccounts]
  );
  const balanceRows = Object.entries(balancesByCurrency)
    .sort(([left], [right]) => left.localeCompare(right));

  return (
    <section className="bank-home">
      <div className="welcome-panel">
        <div>
          <p className="eyebrow">Добро пожаловать</p>
          <h2>{user?.fullName || user?.username}</h2>
          <span>Ваши продукты Kemaless Bank доступны в одном защищенном кабинете.</span>
        </div>
        <button className="icon-button" type="button" onClick={() => onNavigate("accounts")}>
          Открыть счет <Plus size={18} />
        </button>
      </div>

      <section className="page-grid">
        <article className="summary-card">
          <CreditCard size={24} />
          <span>Доступно на активных счетах</span>
          {balanceRows.length > 0 ? (
            <div className="currency-total-list">
              {balanceRows.map(([currency, amount]) => (
                <strong key={currency}>{formatMoney(amount, currency)}</strong>
              ))}
            </div>
          ) : (
            <strong>{moneyFormatter.format(0)}</strong>
          )}
        </article>
        <article className="summary-card">
          <Building2 size={24} />
          <span>Активные счета</span>
          <strong>{activeAccounts.length}</strong>
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <h2><CreditCard size={20} /> Последние счета</h2>
          <button className="secondary" type="button" onClick={() => onNavigate("accounts")}>Все счета</button>
        </div>
        <div className="account-list">
          {accounts.slice(0, 3).map((account) => (
            <div className="account-row" key={account.id}>
              <div>
                <strong>{account.title}</strong>
                <span>{account.accountNumber}</span>
              </div>
              <b>{formatMoney(account.balance, account.currency)}</b>
            </div>
          ))}
          {!accounts.length && <span className="hint">Откройте первый счет, чтобы начать пользоваться банком.</span>}
        </div>
      </section>
    </section>
  );
}

function AccountsPage({ accounts, onAccountsChanged }) {
  const [showForm, setShowForm] = useState(false);
  const [statements, setStatements] = useState({});
  const [loadingStatementId, setLoadingStatementId] = useState(null);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const activeAccountsCount = accounts.filter((account) => account.status === "ACTIVE").length;

  useEffect(() => {
    const validIds = new Set(accounts.map((a) => a.id));
    setStatements((prev) => {
      const next = { ...prev };
      let changed = false;
      for (const key of Object.keys(next)) {
        if (!validIds.has(Number(key))) {
          delete next[key];
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [accounts]);

  async function openAccount(event) {
    event.preventDefault();
    setError("");
    setStatus("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      await api.openAccount({
        title: form.get("title"),
        currency: form.get("currency")
      });
      formElement.reset();
      setShowForm(false);
      setStatus("Счет открыт");
      onAccountsChanged(await api.accounts());
    } catch (error) {
      setError(error.message || "Не удалось открыть счет");
    }
  }

  async function closeAccount(account) {
    setError("");
    setStatus("");
    try {
      await api.closeAccount(account.id);
      setStatus("Счет закрыт");
      onAccountsChanged(await api.accounts());
    } catch (error) {
      setError(error.message || "Не удалось закрыть счет");
    }
  }

  async function blockAccount(account) {
    setError("");
    setStatus("");
    try {
      await api.blockAccount(account.id);
      setStatus("Счет заблокирован");
      onAccountsChanged(await api.accounts());
    } catch (error) {
      setError(error.message || "Не удалось заблокировать счет");
    }
  }

  async function unblockAccount(account) {
    setError("");
    setStatus("");
    try {
      await api.unblockAccount(account.id);
      setStatus("Счет разблокирован");
      onAccountsChanged(await api.accounts());
    } catch (error) {
      setError(error.message || "Не удалось разблокировать счет");
    }
  }

  async function loadStatement(account) {
    setError("");
    setLoadingStatementId(account.id);
    try {
      const data = await api.statement(account.id);
      setStatements((prev) => ({ ...prev, [account.id]: data }));
    } catch (error) {
      setError(error.message || "Не удалось сформировать выписку");
    } finally {
      setLoadingStatementId(null);
    }
  }

  function hideStatement(accountId) {
    setStatements((prev) => {
      const next = { ...prev };
      delete next[accountId];
      return next;
    });
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <h2><CreditCard size={20} /> Счета</h2>
        <button className="icon-button" type="button" onClick={() => setShowForm((value) => !value)}>
          {showForm ? <X size={18} /> : <Plus size={18} />}
          {showForm ? "Отмена" : "Открыть счет"}
        </button>
      </div>

      {showForm && (
        <form className="inline-form" onSubmit={openAccount}>
          <label>Название счета<input name="title" required maxLength={120} placeholder="Накопительный счет" /></label>
          <label>
            Валюта
            <select name="currency" defaultValue="RUB">
              <option value="RUB">RUB</option>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
            </select>
          </label>
          <button type="submit">Открыть</button>
        </form>
      )}

      <div className="account-list">
        {accounts.map((account) => {
          const statement = statements[account.id];
          const canClose = account.status === "ACTIVE" && Number(account.balance) === 0 && activeAccountsCount > 1;
          const closeTitle = Number(account.balance) !== 0
            ? "Закрыть можно только счет с нулевым балансом"
            : activeAccountsCount <= 1
              ? "Нельзя закрыть последний активный счет"
              : "Закрыть счет";
          return (
            <div className="account-block" key={account.id}>
              <div className="account-row">
                <div>
                  <strong>{account.title}</strong>
                  <span>{account.accountNumber}</span>
                  <small className={`status-badge ${account.status.toLowerCase()}`}>{accountStatusLabel(account.status)}</small>
                </div>
                <div className="account-actions">
                  <b>{formatMoney(account.balance, account.currency)}</b>
                  <button
                    className="secondary"
                    type="button"
                    disabled={loadingStatementId === account.id}
                    onClick={() => loadStatement(account)}
                  >
                    {loadingStatementId === account.id ? "Загрузка…" : statement ? "Обновить выписку" : "Выписка"}
                  </button>
                  {account.status === "ACTIVE" && (
                    <button className="secondary" type="button" onClick={() => blockAccount(account)}>
                      Блокировать
                    </button>
                  )}
                  {account.status === "BLOCKED" && (
                    <button className="secondary" type="button" onClick={() => unblockAccount(account)}>
                      Разблокировать
                    </button>
                  )}
                  {account.status === "ACTIVE" && (
                    <button
                      className="secondary"
                      type="button"
                      disabled={!canClose}
                      title={closeTitle}
                      onClick={() => closeAccount(account)}
                    >
                      Закрыть
                    </button>
                  )}
                </div>
              </div>
              {statement && (
                <section className="statement-box statement-box-nested">
                  <div className="panel-heading">
                    <h3>
                      <FileText size={18} /> Операции по счету
                    </h3>
                    <button className="secondary" type="button" onClick={() => hideStatement(account.id)}>
                      Скрыть
                    </button>
                  </div>
                  <div className="operation-list">
                    {statement.operations.map((operation) => (
                      <div className="operation-row" key={operation.id}>
                        <div>
                          <strong>{operation.description}</strong>
                          <span>{new Date(operation.createdAt).toLocaleString("ru-RU")}</span>
                        </div>
                        <b className={operation.type === "TRANSFER_IN" ? "success" : ""}>
                          {operation.type === "TRANSFER_IN" ? "+" : "-"}{formatMoney(operation.amount, operation.currency)}
                        </b>
                      </div>
                    ))}
                    {!statement.operations.length && <span className="hint">Операций по счету за период нет</span>}
                  </div>
                </section>
              )}
            </div>
          );
        })}
        {!accounts.length && <span className="hint">Счета пока не открыты</span>}
      </div>
      {status && <span className="success">{status}</span>}
      {error && <span className="error">{error}</span>}
    </section>
  );
}

function TransfersPage({ accounts, onAccountsChanged, onOperationsChanged, onOpenMiniapps }) {
  const activeAccounts = accounts.filter((account) => account.status === "ACTIVE");
  const [fromAccountId, setFromAccountId] = useState("");
  const [internalStatus, setInternalStatus] = useState("");
  const [internalError, setInternalError] = useState("");

  const fromAccount = useMemo(
    () => activeAccounts.find((a) => String(a.id) === fromAccountId),
    [activeAccounts, fromAccountId]
  );
  const toAccountChoices = useMemo(() => {
    if (!fromAccount) {
      return [];
    }
    return activeAccounts.filter((a) => a.id !== fromAccount.id && a.currency === fromAccount.currency);
  }, [activeAccounts, fromAccount]);

  async function submitInternal(event) {
    event.preventDefault();
    setInternalStatus("");
    setInternalError("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      await api.internalTransfer({
        fromAccountId: Number(form.get("fromAccountId")),
        toAccountId: Number(form.get("toAccountId")),
        amount: Number(form.get("amount")),
        description: form.get("description")
      });
      formElement.reset();
      setFromAccountId("");
      setInternalStatus("Перевод выполнен");
      const [updatedAccounts, operations] = await Promise.all([api.accounts(), api.operations()]);
      onAccountsChanged(updatedAccounts);
      onOperationsChanged(operations);
    } catch (error) {
      setInternalError(error.message || "Не удалось выполнить перевод");
    }
  }

  return (
    <div className="transfers-stack">
      <section className="panel form">
        <h2><ArrowLeftRight size={20} /> Между своими счетами</h2>
        <p className="hint">
          Валюты должны совпадать: зачисление только на счёт в той же валюте, что и списание. Конвертации нет.
        </p>
        <form className="form" onSubmit={submitInternal}>
          <label>
            Списать со счета
            <select
              name="fromAccountId"
              required
              value={fromAccountId}
              onChange={(e) => setFromAccountId(e.target.value)}
            >
              <option value="">Выберите счет</option>
              {activeAccounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.title} — {formatMoney(account.balance, account.currency)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Зачислить на счет
            <select name="toAccountId" required key={fromAccountId || "none"} disabled={!fromAccount || toAccountChoices.length === 0}>
              <option value="">{fromAccount ? "Выберите счет в той же валюте" : "Сначала выберите счет списания"}</option>
              {toAccountChoices.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.title} — {account.currency}
                </option>
              ))}
            </select>
          </label>
          <label>Сумма<input name="amount" required min="0.01" step="0.01" type="number" /></label>
          <label>Назначение<input name="description" maxLength={500} placeholder="Перевод между своими счетами" /></label>
          <button type="submit" disabled={activeAccounts.length < 2 || !fromAccount || toAccountChoices.length === 0}>
            Перевести
          </button>
          {activeAccounts.length < 2 && <span className="hint">Нужны минимум два активных счёта.</span>}
          {fromAccount && toAccountChoices.length === 0 && (
            <span className="hint">Нет второго активного счёта в валюте {fromAccount.currency}. Откройте счёт в этой валюте на странице «Счета».</span>
          )}
          {internalStatus && <span className="success">{internalStatus}</span>}
          {internalError && <span className="error">{internalError}</span>}
        </form>
      </section>

      <section className="transfer-miniapp-callout">
        <div className="callout-icon">
          <PlugZap size={24} />
        </div>
        <div>
          <p className="eyebrow">Платежи другим людям</p>
          <h2>Если хотите отправить деньги другому человеку, воспользуйтесь Платежным сервисом из Miniapps.</h2>
          <p>
            Переводы клиентам вынесены в отдельный miniapp: там работает SSO, отдельная сессия сервиса и серверная
            проверка платежа перед обращением к банку.
          </p>
        </div>
        <button className="icon-button" type="button" onClick={onOpenMiniapps}>
          Открыть Miniapps <ArrowRight size={18} />
        </button>
      </section>
    </div>
  );
}

function OperationsPage({ operations }) {
  return (
    <section className="panel">
      <h2><History size={20} /> История операций</h2>
      <div className="operation-list">
        {operations.map((operation) => (
          <div className="operation-row" key={operation.id}>
            <div>
              <strong>{operation.description}</strong>
              <span>{operation.accountNumber} · {new Date(operation.createdAt).toLocaleString("ru-RU")}</span>
            </div>
            <b className={operation.type === "TRANSFER_IN" ? "success" : ""}>
              {operation.type === "TRANSFER_IN" ? "+" : "-"}{formatMoney(operation.amount, operation.currency)}
            </b>
          </div>
        ))}
        {!operations.length && <span className="hint">Операций пока нет</span>}
      </div>
    </section>
  );
}

function ProfilePage({ user, onSaved }) {
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setStatus("");
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const updated = await api.updateProfile({
        fullName: form.get("fullName"),
        email: form.get("email"),
        phone: form.get("phone")
      });
      onSaved(updated);
      setStatus("Профиль обновлен");
    } catch (error) {
      setError(error.message || "Не удалось сохранить профиль");
    }
  }

  return (
    <div className="page-narrow-centered">
      <form className="panel form profile-form" onSubmit={submit}>
        <h2><UserRound size={20} /> Личный профиль</h2>
        <label>ФИО<input name="fullName" required defaultValue={user.fullName || ""} /></label>
        <label>Email<input name="email" required type="email" defaultValue={user.email || ""} /></label>
        <label>Телефон<input name="phone" defaultValue={user.phone || ""} /></label>
        <button className="icon-button" type="submit"><Save size={18} /> Сохранить профиль</button>
        {status && <span className="success">{status}</span>}
        {error && <span className="error">{error}</span>}
      </form>
    </div>
  );
}

function SecurityPage() {
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [devices, setDevices] = useState([]);
  const [devicesError, setDevicesError] = useState("");

  async function loadDevices() {
    setDevicesError("");
    try {
      const list = await api.myDevices();
      setDevices(Array.isArray(list) ? list : []);
    } catch (err) {
      setDevicesError(err.message || "Не удалось загрузить список устройств");
    }
  }

  useEffect(() => {
    loadDevices();
  }, []);

  async function submit(event) {
    event.preventDefault();
    setStatus("");
    setError("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      await api.changePassword({
        currentPassword: form.get("currentPassword"),
        newPassword: form.get("newPassword")
      });
      formElement.reset();
      setStatus("Пароль изменен. При следующем входе используйте новый пароль.");
      await loadDevices();
    } catch (error) {
      setError(error.message || "Не удалось изменить пароль");
    }
  }

  return (
    <div className="security-split-layout">
      <form className="panel form security-card" onSubmit={submit}>
        <h2><KeyRound size={20} /> Безопасность</h2>
        <label>Текущий пароль<input name="currentPassword" required type="password" autoComplete="current-password" /></label>
        <label>Новый пароль<input name="newPassword" required minLength={8} type="password" autoComplete="new-password" /></label>
        <button type="submit">Обновить пароль</button>
        {status && <span className="success">{status}</span>}
        {error && <span className="error">{error}</span>}
      </form>

      <section className="panel devices-panel security-card">
        <div className="panel-heading">
          <h2><Smartphone size={20} /> Устройства</h2>
          <button className="secondary" type="button" onClick={() => loadDevices()}>
            Обновить
          </button>
        </div>
        <p className="hint">Список обновляется при входе и при продлении сессии.</p>
        {devicesError && <span className="error">{devicesError}</span>}
        <div className="device-list">
          {devices.length === 0 && !devicesError && <span className="hint">Устройств пока нет.</span>}
          {devices.map((device) => (
            <article className="device-row" key={device.id}>
              <div>
                <strong>{device.summary}</strong>
                <span>
                  {device.currentDevice ? "Это устройство" : "Другое устройство"}
                  {device.ipAddress ? ` · ${device.ipAddress}` : ""}
                </span>
                <small>
                  Первый вход: {device.firstSeenAt ? new Date(device.firstSeenAt).toLocaleString("ru-RU") : "—"} · Активность:{" "}
                  {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString("ru-RU") : "—"}
                </small>
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

function MiniappsPage({ miniapps, onOpen }) {
  return (
    <section className="panel miniapp-panel">
      <h2><PlugZap size={20} /> Miniapps</h2>
      <div className="miniapp-list">
        {miniapps.length === 0 && (
          <div className="viewer-state">Дополнительных сервисов пока нет. Когда они появятся, вы сможете открыть их отсюда.</div>
        )}
        {miniapps.map((miniapp) => (
          <article className="miniapp-row" key={miniapp.id}>
            <div>
              <strong>{miniapp.name}</strong>
              <span>{miniapp.status || "—"}</span>
              <small>{miniapp.description || ""}</small>
            </div>
            <div className="account-actions">
              <button type="button" onClick={() => onOpen(miniapp)}>Открыть</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

const navItems = [
  { id: "home", label: "Главная", icon: Home },
  { id: "accounts", label: "Счета", icon: CreditCard },
  { id: "transfers", label: "Переводы", icon: ArrowLeftRight },
  { id: "operations", label: "Операции", icon: History },
  { id: "profile", label: "Профиль", icon: UserRound },
  { id: "security", label: "Безопасность", icon: KeyRound },
  { id: "miniapps", label: "Miniapps", icon: PlugZap }
];

function AppShell({ user, onLogout }) {
  const [currentUser, setCurrentUser] = useState(user);
  const [accounts, setAccounts] = useState([]);
  const [operations, setOperations] = useState([]);
  const [miniapps, setMiniapps] = useState([]);
  const [selectedMiniapp, setSelectedMiniapp] = useState(null);
  const [activePage, setActivePage] = useState("home");
  const [loadError, setLoadError] = useState("");

  async function refreshBankData() {
    try {
      const [userAccounts, userOperations] = await Promise.all([api.accounts(), api.operations()]);
      setLoadError("");
      setAccounts(userAccounts);
      setOperations(userOperations);
    } catch (error) {
      if (isAuthError(error)) {
        onLogout();
        return;
      }
      setLoadError(error.message || "Не удалось обновить данные банка");
    }
  }

  async function closeMiniapp() {
    setSelectedMiniapp(null);
    await refreshBankData();
  }

  useEffect(() => {
    let cancelled = false;

    Promise.all([api.me(), api.accounts(), api.operations()])
      .then(([me, userAccounts, userOperations]) => {
        if (cancelled) {
          return;
        }
        setLoadError("");
        setCurrentUser(me);
        setAccounts(userAccounts);
        setOperations(userOperations);
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        if (isAuthError(error)) {
          onLogout();
          return;
        }
        setLoadError(error.message || "Не удалось загрузить данные банка");
      });

    api.miniapps()
      .then((apps) => {
        if (!cancelled) {
          setMiniapps(Array.isArray(apps) ? apps : []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMiniapps([]);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [onLogout]);

  const pageTitle = navItems.find((item) => item.id === activePage)?.label || "Главная";

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">Kemaless Bank</p>
          <strong>{currentUser?.username}</strong>
        </div>
        <nav>
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button className={activePage === item.id ? "active" : ""} key={item.id} type="button" onClick={() => setActivePage(item.id)}>
                <Icon size={18} />
                {item.label}
              </button>
            );
          })}
        </nav>
        <button className="icon-button secondary" type="button" onClick={onLogout} title="Выйти">
          <LogOut size={18} />
          Выйти
        </button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Мой банк</p>
            <h1>{pageTitle}</h1>
          </div>
        </header>
        {loadError && <div className="notice-error">{loadError}</div>}

        {activePage === "home" && <BankHomePage user={currentUser} accounts={accounts} onNavigate={setActivePage} />}
        {activePage === "accounts" && <AccountsPage accounts={accounts} onAccountsChanged={setAccounts} />}
        {activePage === "transfers" && (
          <TransfersPage
            accounts={accounts}
            onAccountsChanged={setAccounts}
            onOperationsChanged={setOperations}
            onOpenMiniapps={() => setActivePage("miniapps")}
          />
        )}
        {activePage === "operations" && <OperationsPage operations={operations} />}
        {activePage === "profile" && currentUser && <ProfilePage user={currentUser} onSaved={setCurrentUser} />}
        {activePage === "security" && <SecurityPage />}
        {activePage === "miniapps" && <MiniappsPage miniapps={miniapps} onOpen={setSelectedMiniapp} />}
      </section>

      {selectedMiniapp && (
        <MiniappContainer
          miniapp={selectedMiniapp}
          onClose={closeMiniapp}
          onBankDataChanged={refreshBankData}
        />
      )}
    </main>
  );
}

function App() {
  const [user, setUser] = useState(null);
  const [publicScreen, setPublicScreen] = useState("home");
  const [authMode, setAuthMode] = useState("login");
  const [loading, setLoading] = useState(true);
  const [bootError, setBootError] = useState("");

  useEffect(() => {
    if (!authStore.getToken() && !authStore.getRefreshToken()) {
      setLoading(false);
      return;
    }
    api.me()
      .then(setUser)
      .catch((error) => {
        if (isAuthError(error)) {
          authStore.clearToken();
          return;
        }
        setBootError(error.message || "Не удалось подключиться к банку");
      })
      .finally(() => setLoading(false));
  }, []);

  function openAuth(mode) {
    setAuthMode(mode);
    setPublicScreen("auth");
  }

  async function logout() {
    const token = authStore.getToken();
    if (token) {
      try {
        await api.logout();
      } catch {}
    }
    authStore.clearToken();
    setUser(null);
    setPublicScreen("home");
  }

  if (loading) {
    return <main className="viewer-state full-page">Загрузка...</main>;
  }

  if (bootError) {
    return (
      <main className="viewer-state full-page">
        <section className="boot-error">
          <strong>Банк временно недоступен</strong>
          <span>{bootError}</span>
          <button type="button" onClick={() => window.location.reload()}>Повторить</button>
        </section>
      </main>
    );
  }

  if (user) {
    return <AppShell user={user} onLogout={logout} />;
  }

  if (publicScreen === "auth") {
    return <AuthPage initialMode={authMode} onBack={() => setPublicScreen("home")} onLogin={setUser} />;
  }

  return <PublicHome onAuth={openAuth} />;
}

const rootEl = document.getElementById("root");
if (!rootEl) {
  document.body.textContent = "В index.html отсутствует контейнер #root.";
} else {
  createRoot(rootEl).render(
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  );
}

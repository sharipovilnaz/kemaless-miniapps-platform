const params = new URLSearchParams(window.location.search);
const launchContext = Object.fromEntries(params.entries());
const ticket = launchContext.ticket;

let miniappAccessToken = null;

function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function money(amount, currency = "RUB") {
    return new Intl.NumberFormat("ru-RU", { style: "currency", currency }).format(Number(amount || 0));
}

function readableError(error, fallback = "Не удалось выполнить операцию. Попробуйте позже.") {
    const message = String(error?.message || "").trim();
    if (!message) {
        return fallback;
    }
    if (message === "Failed to fetch" || message.includes("NetworkError")) {
        return "Нет связи с платежным сервисом. Проверьте подключение и попробуйте снова.";
    }
    if (message.includes("Требуется вход") || message.includes("401")) {
        return "Сессия платежного сервиса истекла. Закройте окно и откройте его из банка заново.";
    }
    return message;
}

function operationTitle(type) {
    if (type === "TRANSFER_IN") {
        return "Поступление";
    }
    if (type === "TRANSFER_OUT") {
        return "Списание";
    }
    return type || "Операция";
}

async function exchangeSession() {
    let res;
    try {
        res = await fetch("/api/session/exchange", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(launchContext)
        });
    } catch (e) {
        throw new Error("Платежный сервис не смог создать сессию. Попробуйте открыть его заново.");
    }
    if (!res.ok) {
        let detail = res.statusText;
        try {
            const pd = await res.json();
            detail = pd.detail || pd.title || detail;
        } catch {}
        throw new Error(detail || "Не удалось создать сессию платежного сервиса");
    }
    if (res.status === 204) {
        throw new Error("Не удалось создать сессию платежного сервиса");
    }
    const text = await res.text();
    if (!text) {
        throw new Error("Не удалось создать сессию платежного сервиса");
    }
    return JSON.parse(text);
}

async function fetchJson(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = options.headers || {};
    let res;
    try {
        res = await fetch(path, { ...options, method, headers });
    } catch {
        return null;
    }
    const text = await res.text();
    if (!res.ok) {
        let detail = res.statusText;
        try {
            const pd = JSON.parse(text);
            detail = pd.detail || pd.title || detail;
        } catch {
            detail = text || detail;
        }
        throw new Error(detail || `HTTP ${res.status}`);
    }
    if (res.status === 204 || !text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch {
        throw new Error("Не удалось разобрать ответ сервиса");
    }
}

async function paymentApi(path, options = {}) {
    if (!miniappAccessToken) {
        return null;
    }
    const method = (options.method || "GET").toUpperCase();
    const headers = {
        Authorization: `Bearer ${miniappAccessToken}`,
        ...options.headers
    };
    if (method !== "GET" && method !== "HEAD" && options.body != null) {
        headers["Content-Type"] = headers["Content-Type"] || "application/json";
    }
    return fetchJson(`/api/payment${path}`, { ...options, method, headers });
}

function render(root, session, state) {
    const user = session.user;
    const bankUser = state.bankMe;
    const who =
        bankUser && bankUser.username
            ? `${esc(bankUser.username)} (${esc(bankUser.fullName || "")})`
            : esc(user?.externalUserId || "unknown");
    const accountsOptions = (state.accounts || [])
        .filter((a) => a.status === "ACTIVE")
        .map(
            (a) =>
                `<option value="${a.id}">${esc(a.title)} · ${esc(a.accountNumber)} · ${esc(money(a.balance, a.currency))}</option>`
        )
        .join("");

    root.innerHTML = `
    <div class="shell">
      <header class="miniapp-hero">
        <div>
          <p class="eyebrow">Kemaless Bank</p>
          <h1>Платежный сервис</h1>
          <p class="muted">Вы вошли как <strong>${who}</strong></p>
        </div>
        <span class="badge">Сессия активна</span>
      </header>

      <section class="panel">
        <div class="panel-title-row">
          <h2>Счета для списания</h2>
          <button type="button" class="secondary" id="btn-refresh-accounts" ${state.accountsLoading ? "disabled" : ""}>
            ${state.accountsLoading ? "Обновляем..." : "Обновить"}
          </button>
        </div>
        ${state.hostError ? `<p class="error">${esc(state.hostError)}</p>` : ""}
        ${
        state.accounts?.length
            ? `<ul class="list">${state.accounts
                .map(
                    (a) =>
                        `<li><span><strong>${esc(a.title)}</strong><br/><span class="muted">${esc(a.accountNumber)} · ${esc(a.status)}</span></span><span>${esc(money(a.balance, a.currency))}</span></li>`
                )
                .join("")}</ul>`
            : `<p class="muted">Нет активных счетов для платежей.</p>`
    }
      </section>

      <section class="panel payment-panel">
        <h2>Перевод другому клиенту</h2>
        <p class="muted">Получатель — <strong>логин</strong> или <strong>телефон</strong>. Зачисление: на указанный номер счёта или на первый активный счёт получателя в валюте списания.</p>
        <form id="form-peer">
          <div class="row">
            <label>Счёт списания
              <select name="fromAccountId" required>${accountsOptions || '<option value="">— нет счетов —</option>'}</select>
            </label>
            <label>Сумма
              <input name="amount" type="number" step="0.01" min="0.01" required placeholder="500.00" />
            </label>
          </div>
          <label style="margin-top:12px">Логин или телефон получателя
            <input name="recipient" required maxlength="64" placeholder="friend_user или +7 900 000-00-00" autocomplete="off" />
          </label>
          <label style="margin-top:12px">Номер счёта получателя (необязательно)
            <input name="toAccountNumber" maxlength="34" placeholder="Если не указан — авто по валюте" autocomplete="off" />
          </label>
          <label style="margin-top:12px">Комментарий (необязательно)
            <input name="description" maxlength="500" placeholder="Например: возврат долга" />
          </label>
          <div class="row" style="margin-top:14px">
            <button type="submit" ${accountsOptions && !state.peerSubmitting ? "" : "disabled"}>
              ${state.peerSubmitting ? "Отправляем..." : "Отправить перевод"}
            </button>
          </div>
        </form>
        <p id="peer-status" class="${state.peerOk ? "success" : "error"}" style="margin-top:10px">${state.peerMessage ? esc(state.peerMessage) : ""}</p>
      </section>

      <section class="panel">
        <div class="panel-title-row">
          <h2>Последние операции</h2>
          <button type="button" class="secondary" id="btn-refresh-ops" ${state.opsLoading ? "disabled" : ""}>
            ${state.opsLoading ? "Обновляем..." : "Обновить"}
          </button>
        </div>
        ${
        state.operations?.length
            ? `<ul class="list">${state.operations
                .slice(0, 30)
                .map(
                    (o) =>
                        `<li><span><strong>${esc(operationTitle(o.type))}</strong><br/><span class="muted">${esc(o.description || "—")}</span></span><span>${o.type === "TRANSFER_IN" ? "+" : "-"}${esc(money(o.amount, o.currency))}</span></li>`
                )
                .join("")}</ul>`
            : `<p class="muted">Операций пока нет.</p>`
    }
      </section>
    </div>
  `;

    document.getElementById("btn-refresh-accounts").onclick = () => state.onRefreshAccounts();
    document.getElementById("btn-refresh-ops").onclick = () => state.onRefreshOps();
    document.getElementById("form-peer").onsubmit = (e) => state.onPeerSubmit(e);
}

async function main() {
    const root = document.getElementById("app");
    if (!ticket) {
        root.innerHTML = `<div class="shell"><section class="panel boot-panel"><h1>Платежный сервис</h1><p class="error">Не удалось получить параметры запуска. Откройте сервис из банка заново.</p></section></div>`;
        return;
    }

    let session;
    try {
        session = await exchangeSession();
        miniappAccessToken = session.accessToken || null;
        window.parent?.postMessage({ type: "MINIAPP_READY" }, "*");
    } catch (e) {
        window.parent?.postMessage({
            type: "ERROR",
            payload: {
                code: "AUTH_FAILED",
                message: readableError(e, "Не удалось войти в платежный сервис.")
            }
        }, "*");
        root.innerHTML = `<div class="shell"><section class="panel boot-panel"><h1>Платежный сервис</h1><p class="error">${esc(readableError(e, "Не удалось войти в платежный сервис."))}</p><p class="muted">Закройте окно и откройте сервис из банка заново.</p></section></div>`;
        return;
    }

    const state = {
        accounts: [],
        operations: [],
        bankMe: null,
        hostError: "",
        peerMessage: "",
        peerOk: false,
        peerSubmitting: false,
        accountsLoading: false,
        opsLoading: false,
        async onRefreshAccounts() {
            this.hostError = "";
            this.accountsLoading = true;
            render(root, session, this);
            try {
                this.accounts = await paymentApi("/accounts");
                if (!Array.isArray(this.accounts)) {
                    this.accounts = [];
                }
            } catch (e) {
                this.hostError = readableError(e, "Не удалось загрузить счета.");
            } finally {
                this.accountsLoading = false;
            }
            render(root, session, this);
        },
        async onRefreshMe() {
            try {
                this.bankMe = await paymentApi("/me");
            } catch {
                this.bankMe = null;
            }
        },
        async onRefreshOps() {
            this.hostError = "";
            this.opsLoading = true;
            render(root, session, this);
            try {
                this.operations = await paymentApi("/operations");
                if (!Array.isArray(this.operations)) {
                    this.operations = [];
                }
            } catch (e) {
                this.hostError = readableError(e, "Не удалось загрузить операции.");
            } finally {
                this.opsLoading = false;
            }
            render(root, session, this);
        },
        async onPeerSubmit(event) {
            event.preventDefault();
            this.peerMessage = "";
            const form = event.currentTarget;
            const fd = new FormData(form);
            const fromAccountId = Number(fd.get("fromAccountId"));
            const amount = Number(fd.get("amount"));
            const recipient = String(fd.get("recipient") || "").trim();
            const description = String(fd.get("description") || "").trim();
            const toAccountNumber = String(fd.get("toAccountNumber") || "").trim();
            if (!fromAccountId || !recipient || !Number.isFinite(amount) || amount < 0.01) {
                this.peerOk = false;
                this.peerMessage = "Проверьте счёт списания, получателя и сумму перевода.";
                render(root, session, this);
                return;
            }
            this.peerSubmitting = true;
            render(root, session, this);
            try {
                await paymentApi("/transfers/peer", {
                    method: "POST",
                    body: JSON.stringify({
                        fromAccountId,
                        recipient,
                        amount,
                        description: description || null,
                        toAccountNumber: toAccountNumber || null
                    })
                });
                this.peerOk = true;
                this.peerMessage = "Перевод выполнен.";
                window.parent?.postMessage({ type: "KEMALESS_BANK_DATA_CHANGED" }, "*");
                form.reset();
                await this.onRefreshAccounts();
                await this.onRefreshOps();
            } catch (e) {
                this.peerOk = false;
                this.peerMessage = readableError(e, "Не удалось выполнить перевод.");
            } finally {
                this.peerSubmitting = false;
            }
            render(root, session, this);
        }
    };

    await state.onRefreshMe();
    await state.onRefreshAccounts();
    await state.onRefreshOps();
    render(root, session, state);
}

document.addEventListener("DOMContentLoaded", () => {
    main().catch((e) => {
        const root = document.getElementById("app");
        if (root) {
            root.innerHTML = `<div class="shell"><section class="panel boot-panel"><p class="error">${esc(readableError(e))}</p></section></div>`;
        }
    });
});

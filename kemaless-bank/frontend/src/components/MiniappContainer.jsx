import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { api } from "../services/api";

export function MiniappContainer({ miniapp, onClose, onBankDataChanged }) {
  const iframeRef = useRef(null);
  const [launchSession, setLaunchSession] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function prepareLaunch() {
      try {
        const session = await api.launchMiniapp(miniapp.id, {});
        if (!cancelled) {
          setLaunchSession(session);
        }
      } catch {
        if (!cancelled) {
          setError("Не удалось открыть сервис. Попробуйте ещё раз позже.");
        }
      }
    }

    prepareLaunch();
    return () => {
      cancelled = true;
    };
  }, [miniapp.id]);

  useEffect(() => {
    function handleMiniappMessage(event) {
      if (event.source !== iframeRef.current?.contentWindow) {
        return;
      }
      if (event.data?.type === "KEMALESS_BANK_DATA_CHANGED") {
        onBankDataChanged?.();
      }
    }

    window.addEventListener("message", handleMiniappMessage);
    return () => window.removeEventListener("message", handleMiniappMessage);
  }, [onBankDataChanged]);

  const iframeSrc = launchSession?.launchUrl ?? "";

  return (
    <div className="viewer-backdrop" role="dialog" aria-modal="true">
      <section className="viewer">
        <header className="viewer-header">
          <div>
            <strong>{miniapp.name}</strong>
            <span>
              {launchSession
                ? `Сервис готов · ссылка ~${launchSession.ticketExpiresIn ?? 60} с`
                : "Подключаем сервис…"}
            </span>
          </div>
          <button className="icon-only secondary" type="button" onClick={onClose} title="Close miniapp">
            <X size={18} />
          </button>
        </header>

        {error && <div className="viewer-state">{error}</div>}
        {!error && !iframeSrc && <div className="viewer-state">Загрузка…</div>}
        {iframeSrc && (
          <iframe
            ref={iframeRef}
            title={miniapp.name}
            src={iframeSrc}
            loading="eager"
            sandbox="allow-scripts allow-forms allow-same-origin allow-popups allow-downloads"
            referrerPolicy="no-referrer"
          />
        )}
      </section>
    </div>
  );
}

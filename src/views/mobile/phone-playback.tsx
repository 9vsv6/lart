import { Suspense, lazy, useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { ChevronLeft } from "lucide-react";
import { useT } from "@/lib/i18n";
import { isPhoneApp } from "@/lib/platform";
import { runPhoneBackHandler } from "@/lib/phone-back";
import { useView } from "@/lib/view";

const PlayPicker = lazy(() =>
  import("@/views/play-picker").then((m) => ({ default: m.PlayPicker })),
);
const PlayerView = lazy(() => import("@/views/player").then((m) => ({ default: m.PlayerView })));
const Settings = lazy(() => import("@/views/settings").then((m) => ({ default: m.Settings })));
const AddonsView = lazy(() => import("@/views/addons").then((m) => ({ default: m.AddonsView })));

/**
 * The standalone phone app plays streams on the phone itself. MobileShell has
 * no desktop Shell underneath it, so the views a phone needs from the desktop
 * (settings, addons, the stream picker and the player) are layered here, full
 * screen, over the touch UI.
 */
export function PhonePlayback() {
  const t = useT();
  const { picker, player, topKind, canGoBack, goBack, setView, exitPlayback } = useView();
  const page = topKind === "settings" || topKind === "addons" ? topKind : null;
  usePhoneBackButton({
    hasPlayer: !!player,
    hasPicker: !!picker,
    hasPage: !!page,
    canGoBack,
    goBack,
    goHome: () => setView("home"),
    exitPlayback,
  });
  const playerActive = !!player && !player.sportsDocked && !player.pipDocked;
  usePlayerWindowMode(playerActive);
  useHideUnderlay(playerActive);
  if (!isPhoneApp()) return null;
  // Portaled to <body>: the shell sits in its own z-30 stacking context, and
  // the mobile detail sheet is a z-50 body portal that would otherwise cover
  // these layers no matter their z-index.
  return createPortal(
    <div data-phone-overlays>
      {page && !playerActive && (
        <div
          className="fixed inset-0 z-[60] flex flex-col overflow-hidden bg-canvas"
          style={{
            paddingTop: "env(safe-area-inset-top, 0px)",
            paddingBottom: "env(safe-area-inset-bottom, 0px)",
          }}
        >
          <div className="flex h-12 shrink-0 items-center px-2">
            <button
              type="button"
              onClick={() => {
                if (runPhoneBackHandler()) return;
                if (canGoBack) goBack();
                else setView("home");
              }}
              className="flex h-10 items-center gap-1.5 rounded-full pe-4 ps-2 text-[15px] font-medium text-ink-muted transition-colors active:bg-elevated"
            >
              <ChevronLeft size={22} strokeWidth={2} />
              {t("Back")}
            </button>
          </div>
          <div className="relative min-h-0 flex-1">
            <Suspense fallback={null}>
              {page === "settings" ? <Settings visible /> : <AddonsView active />}
            </Suspense>
          </div>
        </div>
      )}
      {picker && !playerActive && (
        <div
          className="fixed inset-0 z-[70] flex flex-col overflow-y-auto bg-canvas"
          style={{
            paddingTop: "env(safe-area-inset-top, 0px)",
            paddingBottom: "env(safe-area-inset-bottom, 0px)",
          }}
        >
          <Suspense fallback={null}>
            <PlayPicker
              key={`picker-${picker.meta.id}-${picker.episode?.season ?? ""}-${picker.episode?.episode ?? ""}-${picker.attempt ?? 0}-${picker.intent ?? "play"}`}
              meta={picker.meta}
              episode={picker.episode}
              autoPlay={picker.intent === "download" ? false : picker.autoPlay}
              attempt={picker.attempt}
              intent={picker.intent}
              seasonEpisodes={picker.seasonEpisodes}
              resume={picker.resume}
              playerActive={playerActive}
            />
          </Suspense>
        </div>
      )}
      {player && (
        <div data-phone-player className="fixed inset-0 z-[80] bg-black">
          <Suspense fallback={null}>
            <PlayerView
              key={player.meta.id.startsWith("iptv:") ? "player-live" : `player-${player.meta.id}`}
              src={player}
            />
          </Suspense>
        </div>
      )}
    </div>,
    document.body,
  );
}

/**
 * While the player is open, hide everything already on the page (the app and
 * any open detail sheet, which is its own body portal). The loading screen is
 * translucent and native video draws behind a transparent page, so anything
 * left visible would show through. Layers the player opens later (menus,
 * dialogs) are added after this runs and stay visible.
 */
function useHideUnderlay(active: boolean) {
  useEffect(() => {
    if (!active || !isPhoneApp()) return;
    const hidden: Array<[HTMLElement, string]> = [];
    for (const el of Array.from(document.body.children)) {
      if (!(el instanceof HTMLElement) || el.hasAttribute("data-phone-overlays")) continue;
      if (el.tagName === "SCRIPT" || el.tagName === "STYLE") continue;
      hidden.push([el, el.style.visibility]);
      el.style.visibility = "hidden";
    }
    return () => {
      for (const [el, visibility] of hidden) el.style.visibility = visibility;
    };
  }, [active]);
}

type LartAndroidBridge = { setPlayerMode(on: boolean): void };

/** Landscape lock and hidden system bars while the player is open (MainActivity.kt). */
function usePlayerWindowMode(active: boolean) {
  useEffect(() => {
    if (!isPhoneApp()) return;
    const bridge = (window as unknown as { LartAndroid?: LartAndroidBridge }).LartAndroid;
    if (!bridge) return;
    bridge.setPlayerMode(active);
    return () => {
      if (active) bridge.setPlayerMode(false);
    };
  }, [active]);
}

type BackState = {
  hasPlayer: boolean;
  hasPicker: boolean;
  hasPage: boolean;
  canGoBack: boolean;
  goBack: () => void;
  goHome: () => void;
  exitPlayback: () => void;
};

/** True while a modal dialog is on screen; those close on Escape. */
function dialogOpen(): boolean {
  return [...document.querySelectorAll<HTMLElement>('[role="dialog"], [aria-modal="true"]')].some(
    (el) => el.getClientRects().length > 0,
  );
}

/**
 * Android's back gesture: close an open dialog, then the player (with its
 * picker), the picker or page, then follow the webview's history, and only
 * leave the app when there is nothing left. Settings and addons are opened with setView, which replaces
 * the view stack, so with nothing to pop they return to the base view.
 */
function usePhoneBackButton(next: BackState) {
  const state = useRef(next);
  state.current = next;
  useEffect(() => {
    if (!isPhoneApp()) return;
    let disposed = false;
    let unlisten: (() => void) | undefined;
    void import("@tauri-apps/api/app")
      .then(({ onBackButtonPress, exit }) =>
        onBackButtonPress(({ canGoBack }) => {
          const s = state.current;
          const escape = () =>
            (document.activeElement ?? document.body).dispatchEvent(
              new KeyboardEvent("keydown", { key: "Escape", code: "Escape", bubbles: true }),
            );
          if (dialogOpen()) escape();
          else if (s.hasPlayer) {
            // The player's own Esc path saves the resume point and closes the
            // picker too; going back to the picker would just autoplay again.
            escape();
            window.setTimeout(() => {
              if (state.current.hasPlayer) state.current.exitPlayback();
            }, 400);
          } else if (s.hasPicker) s.goBack();
          else if (s.hasPage) {
            if (runPhoneBackHandler()) return;
            if (s.canGoBack) s.goBack();
            else s.goHome();
          } else if (canGoBack) window.history.back();
          else void exit(0).catch(() => {});
        }),
      )
      .then((listener) => {
        if (disposed) void listener.unregister();
        else unlisten = () => void listener.unregister();
      })
      .catch(() => {});
    return () => {
      disposed = true;
      unlisten?.();
    };
  }, []);
}

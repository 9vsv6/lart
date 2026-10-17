import { isPhoneApp } from "@/lib/platform";

// Android phones play video with Media3 ExoPlayer (NativePlayer.kt) drawn
// behind the transparent WebView. The HTML5 bridge keeps doing everything
// else (subtitles, tracks, progress, the controls), and talks to the native
// player through a <video> element whose media API is redirected here.

type NativeAudio = { id: string; label: string; lang: string; selected: boolean; supported: boolean };

type NativeState = {
  pos: number;
  dur: number;
  buf: number;
  pwr: boolean;
  playing: boolean;
  state: "idle" | "buffering" | "ready" | "ended";
  w: number;
  h: number;
  rate: number;
  vol: number;
  audio: NativeAudio[];
  error?: string;
};

type LartAndroid = {
  nativeLoad(url: string, headersJson: string, startMs: number): void;
  nativePlay(): void;
  nativePause(): void;
  nativeSeek(ms: number): void;
  nativeSetVolume(v: number): void;
  nativeSetRate(r: number): void;
  nativeSelectAudio(id: string): void;
  nativeStop(): void;
};

declare global {
  interface Window {
    LartAndroid?: Partial<LartAndroid>;
    __lartNative?: (state: NativeState) => void;
  }
}

const ROOT_CLASS = "lart-native-video";

function bridge(): LartAndroid | null {
  const b = typeof window !== "undefined" ? window.LartAndroid : undefined;
  return b && typeof b.nativeLoad === "function" ? (b as LartAndroid) : null;
}

/** True on the Android phone app when the native player is available. */
export function nativeVideoAvailable(): boolean {
  return isPhoneApp() && bridge() != null;
}

function ranges(end: number): TimeRanges {
  const has = end > 0;
  return {
    length: has ? 1 : 0,
    start: () => 0,
    end: () => end,
  } as TimeRanges;
}

export type NativeVideoElement = HTMLVideoElement & {
  lartLoad(url: string, headers?: Record<string, string>): void;
};

/**
 * A real, empty <video> element (so layout and listeners behave) whose media
 * properties, methods and events are backed by the native player.
 */
export function createNativeVideo(): NativeVideoElement {
  const native = bridge();
  if (!native) throw new Error("Native player unavailable");
  const v = document.createElement("video") as NativeVideoElement;
  v.style.background = "transparent";

  let st: NativeState = {
    pos: 0,
    dur: -1,
    buf: 0,
    pwr: false,
    playing: false,
    state: "idle",
    w: 0,
    h: 0,
    rate: 1,
    vol: 1,
    audio: [],
  };
  let src = "";
  let volume = 1;
  let muted = false;
  let seekingTo: number | null = null;
  let metadataSent = false;
  let lastError: string | undefined;
  let pendingStartMs = 0;

  const fire = (type: string) => v.dispatchEvent(new Event(type));
  const def = <T>(key: string, get: () => T, set?: (value: T) => void) =>
    Object.defineProperty(v, key, { configurable: true, get, set });

  const applyVolume = () => native.nativeSetVolume(muted ? 0 : volume);

  def("currentTime", () => seekingTo ?? st.pos, (t: number) => {
    if (!Number.isFinite(t)) return;
    if (!src) {
      pendingStartMs = Math.max(0, t * 1000);
      return;
    }
    seekingTo = Math.max(0, t);
    fire("seeking");
    native.nativeSeek(seekingTo * 1000);
  });
  def("duration", () => (st.dur > 0 ? st.dur : NaN));
  def("paused", () => !st.pwr);
  def("ended", () => st.state === "ended");
  def("seeking", () => seekingTo != null);
  def("readyState", () => (st.state === "ready" || st.state === "ended" ? 4 : st.dur > 0 ? 1 : 0));
  def("networkState", () => (src ? 2 : 0));
  def("buffered", () => ranges(st.buf));
  def("seekable", () => ranges(st.dur > 0 ? st.dur : 0));
  def("videoWidth", () => st.w);
  def("videoHeight", () => st.h);
  def("error", () => (lastError ? { code: 4, message: lastError, MEDIA_ERR_SRC_NOT_SUPPORTED: 4 } : null));
  def("volume", () => volume, (x: number) => {
    volume = Math.min(1, Math.max(0, x));
    applyVolume();
    fire("volumechange");
  });
  def("muted", () => muted, (m: boolean) => {
    muted = !!m;
    applyVolume();
    fire("volumechange");
  });
  def("playbackRate", () => st.rate, (r: number) => {
    st.rate = r;
    native.nativeSetRate(r);
    fire("ratechange");
  });
  def("src", () => src, (url: string) => v.lartLoad(url));

  // Audio tracks as an AudioTrackList-shaped object; setting `enabled` picks a track.
  const audioListeners = new Set<EventListenerOrEventListenerObject>();
  const audioTracks = {
    get length() {
      return st.audio.length;
    },
    addEventListener: (_: string, l: EventListenerOrEventListenerObject) => audioListeners.add(l),
    removeEventListener: (_: string, l: EventListenerOrEventListenerObject) =>
      audioListeners.delete(l),
  } as Record<string | number, unknown>;
  const syncAudioIndexes = () => {
    for (let i = 0; i < 16; i++) delete audioTracks[i];
    st.audio.forEach((a, i) => {
      audioTracks[i] = {
        id: a.id,
        label: a.label,
        language: a.lang,
        get enabled() {
          return a.selected;
        },
        set enabled(on: boolean) {
          if (on && !a.selected) native.nativeSelectAudio(a.id);
        },
      };
    });
  };
  def("audioTracks", () => audioTracks);

  v.play = () => {
    native.nativePlay();
    st.pwr = true;
    fire("play");
    return Promise.resolve();
  };
  v.pause = () => {
    native.nativePause();
    if (st.pwr) {
      st.pwr = false;
      fire("pause");
    }
  };
  v.load = () => {
    if (!src) native.nativeStop();
  };
  const removeAttribute = v.removeAttribute.bind(v);
  v.removeAttribute = (name: string) => {
    if (name === "src" && src) {
      src = "";
      native.nativeStop();
      document.documentElement.classList.remove(ROOT_CLASS);
    }
    removeAttribute(name);
  };

  v.lartLoad = (url: string, headers?: Record<string, string>) => {
    src = url;
    metadataSent = false;
    lastError = undefined;
    seekingTo = null;
    st = { ...st, pos: 0, dur: -1, buf: 0, pwr: false, playing: false, state: "idle", audio: [] };
    document.documentElement.classList.add(ROOT_CLASS);
    native.nativeLoad(url, JSON.stringify(headers ?? {}), pendingStartMs);
    pendingStartMs = 0;
    applyVolume();
    fire("loadstart");
  };

  window.__lartNative = (next: NativeState) => {
    if (!src) return;
    const prev = st;
    st = next;
    if (next.error && next.error !== lastError) {
      lastError = next.error;
      fire("error");
      return;
    }
    if (JSON.stringify(prev.audio) !== JSON.stringify(next.audio)) {
      syncAudioIndexes();
      const ev = new Event("change");
      for (const l of audioListeners) {
        if (typeof l === "function") l(ev);
        else l.handleEvent(ev);
      }
    }
    if (next.dur > 0 && !metadataSent) {
      metadataSent = true;
      fire("durationchange");
      fire("loadedmetadata");
      fire("loadeddata");
    } else if (next.dur !== prev.dur && next.dur > 0) {
      fire("durationchange");
    }
    if (next.state === "ready" && prev.state !== "ready") fire("canplay");
    if (seekingTo != null && next.state !== "buffering" && Math.abs(next.pos - seekingTo) < 2) {
      seekingTo = null;
      fire("seeked");
    }
    if (next.state === "buffering" && prev.state !== "buffering") fire("waiting");
    if (next.playing && !prev.playing) fire("playing");
    if (next.pwr !== prev.pwr) fire(next.pwr ? "play" : "pause");
    if (next.buf !== prev.buf) fire("progress");
    if (next.pos !== prev.pos) fire("timeupdate");
    if (next.state === "ended" && prev.state !== "ended") fire("ended");
  };

  return v;
}

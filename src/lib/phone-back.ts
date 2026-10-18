/**
 * One view at a time may claim the phone app's Back (the on-screen Back bar and
 * Android's back gesture) before it closes the page. Settings uses it to step
 * from a page back to its page list. The handler returns true when it handled Back.
 */
let handler: (() => boolean) | null = null;

export function setPhoneBackHandler(next: () => boolean): () => void {
  handler = next;
  return () => {
    if (handler === next) handler = null;
  };
}

export function runPhoneBackHandler(): boolean {
  return handler?.() ?? false;
}

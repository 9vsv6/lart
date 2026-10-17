// The Lart mark: an L with an accent dot. Kept under the HarborMark name so
// every existing call site and the `harbor-mark-sail` theme hook keep working.
export function HarborMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 100 100" fill="currentColor" aria-hidden className={className}>
      <path d="M18 8h20v58h46v22H18z" />
      <circle className="harbor-mark-sail" cx="74" cy="22" r="13" fill="#e8a95c" />
    </svg>
  );
}

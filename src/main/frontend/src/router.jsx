import { useEffect, useState, useCallback } from 'react';

// Minimal History-API router (no routing library: the charting library is the only
// added dependency). Renders on pushState/popstate and exposes navigate + Link.

export function navigate(to) {
  if (to === window.location.pathname + window.location.search) return;
  window.history.pushState({}, '', to);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function useRoute() {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);
  return path;
}

export function Link({ to, children, style, className, title }) {
  const onClick = useCallback(
    (e) => {
      // Let modified clicks open a new tab as usual.
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) return;
      e.preventDefault();
      navigate(to);
    },
    [to],
  );
  return (
    <a href={to} onClick={onClick} style={style} className={className} title={title}>
      {children}
    </a>
  );
}

import { C, SANS } from '../theme.js';
import { Link } from '../router.jsx';

// Placeholder: the full pair terminal (chart, book, tape, trade panel, simulator,
// invariants) is built in the next substep.
export function TerminalPage({ pair }) {
  return (
    <div
      style={{
        minHeight: '100vh',
        background: C.deepBg,
        color: C.text,
        fontFamily: SANS,
        padding: 24,
      }}
    >
      <Link to="/markets">← Markets</Link>
      <h1 style={{ marginTop: 16, fontSize: 20 }}>{pair} terminal</h1>
      <p style={{ color: C.muted, marginTop: 8 }}>Coming up next.</p>
    </div>
  );
}

import { C, SANS } from '../theme.js';

// A bordered dark card matching the terminal design, with an uppercase header row.
export function Panel({ title, right, children, style, bodyStyle }) {
  return (
    <section
      style={{
        display: 'flex',
        flexDirection: 'column',
        background: C.panelBg,
        border: `1px solid ${C.card2}`,
        borderRadius: 11,
        boxShadow: '0 8px 24px rgba(0,0,0,.4)',
        overflow: 'hidden',
        minHeight: 0,
        ...style,
      }}
    >
      {title && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '11px 14px',
            borderBottom: `1px solid ${C.card2}`,
            fontSize: 11.5,
            letterSpacing: '.09em',
            textTransform: 'uppercase',
            color: C.muted,
            fontFamily: SANS,
            flex: '0 0 auto',
          }}
        >
          <span>{title}</span>
          {right}
        </div>
      )}
      <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', ...bodyStyle }}>
        {children}
      </div>
    </section>
  );
}

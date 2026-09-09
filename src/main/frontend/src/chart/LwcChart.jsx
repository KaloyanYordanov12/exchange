import { useEffect, useRef } from 'react';
import { createChart, CrosshairMode, LineStyle } from 'lightweight-charts';
import { C, MONO, SANS } from '../theme.js';
import { priceUsd, priceDecimals } from '../format.js';

// Default number of most-recent candles shown on load, like TradingView/Binance: a
// readable window, not the whole history squeezed in. The viewer scrolls/zooms for
// older data; the price axis auto-scales to whatever is visible.
const DEFAULT_VIEW = 120;

// Candle/line price chart with a volume panel, crosshair, and a SEEDED/LIVE divider,
// rendered with TradingView Lightweight Charts and skinned to the design. All data is
// the real backend candle series (scaled integers converted to USD for display).
export function LwcChart({ candles, realBoundary, pairInfo, type, fitKey, onHover }) {
  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const mainRef = useRef(null);
  const volRef = useRef(null);
  const bandRef = useRef(null);
  const lineRef = useRef(null);
  const byTimeRef = useRef(new Map());
  const fitKeyRef = useRef(null);
  const followingRef = useRef(true); // true while the viewer is at the right (newest) edge
  const barCountRef = useRef(0);
  const decimals = priceDecimals(pairInfo.tickSize);

  // Create the chart once.
  useEffect(() => {
    const el = containerRef.current;
    const chart = createChart(el, {
      layout: {
        background: { color: 'transparent' },
        textColor: C.muted,
        fontFamily: SANS,
        fontSize: 11,
      },
      grid: {
        vertLines: { color: C.hair2 },
        horzLines: { color: C.hair2 },
      },
      rightPriceScale: {
        borderColor: C.border,
        scaleMargins: { top: 0.08, bottom: 0.26 },
      },
      timeScale: {
        borderColor: C.border,
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: C.border3, width: 1, style: LineStyle.Dashed, labelBackgroundColor: C.border3 },
        horzLine: { color: C.border3, width: 1, style: LineStyle.Dashed, labelBackgroundColor: C.border3 },
      },
      handleScale: true,
      handleScroll: true,
    });
    chartRef.current = chart;

    const ro = new ResizeObserver(() => {
      chart.applyOptions({ width: el.clientWidth, height: el.clientHeight });
      positionDivider();
    });
    ro.observe(el);
    chart.applyOptions({ width: el.clientWidth, height: el.clientHeight });

    chart.timeScale().subscribeVisibleTimeRangeChange(positionDivider);

    // Track whether the viewer is pinned to the newest bar. If they scroll back to
    // older data we stop auto-advancing; when they return to the right edge we resume.
    chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
      if (!range) return;
      followingRef.current = range.to >= barCountRef.current - 1.5;
    });

    chart.subscribeCrosshairMove((param) => {
      if (!param || !param.time || !param.point) {
        onHover(null);
        return;
      }
      onHover(byTimeRef.current.get(param.time) || null);
    });

    return () => {
      ro.disconnect();
      chart.remove();
      chartRef.current = null;
      mainRef.current = null;
      volRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Build (or rebuild) the price + volume series when the chart type changes.
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    if (mainRef.current) {
      chart.removeSeries(mainRef.current);
      mainRef.current = null;
    }
    if (volRef.current) {
      chart.removeSeries(volRef.current);
      volRef.current = null;
    }
    const priceFormat = {
      type: 'price',
      precision: decimals,
      minMove: 1 / Math.pow(10, decimals),
    };
    if (type === 'line') {
      mainRef.current = chart.addAreaSeries({
        lineColor: C.amber,
        topColor: 'rgba(255,176,32,0.22)',
        bottomColor: 'rgba(255,176,32,0.0)',
        lineWidth: 2,
        priceFormat,
        priceLineColor: C.amber,
      });
    } else {
      mainRef.current = chart.addCandlestickSeries({
        upColor: C.green,
        downColor: C.red,
        borderVisible: false,
        wickUpColor: C.green,
        wickDownColor: C.red,
        priceFormat,
      });
    }
    volRef.current = chart.addHistogramSeries({
      priceScaleId: 'vol',
      priceFormat: { type: 'volume' },
      priceLineVisible: false,
      lastValueVisible: false,
    });
    chart.priceScale('vol').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    renderData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type]);

  // Push data whenever the candle series changes.
  useEffect(() => {
    renderData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candles, realBoundary, fitKey]);

  function renderData() {
    const main = mainRef.current;
    const vol = volRef.current;
    if (!main || !vol) return;
    const map = new Map();
    const priceData = [];
    const volData = [];
    for (const c of candles) {
      map.set(c.time, c);
      const up = c.close >= c.open;
      if (type === 'line') {
        priceData.push({ time: c.time, value: priceUsd(c.close) });
      } else {
        priceData.push({
          time: c.time,
          open: priceUsd(c.open),
          high: priceUsd(c.high),
          low: priceUsd(c.low),
          close: priceUsd(c.close),
        });
      }
      volData.push({
        time: c.time,
        value: c.volume,
        color: up ? 'rgba(14,206,134,0.5)' : 'rgba(255,87,102,0.5)',
      });
    }
    byTimeRef.current = map;
    main.setData(priceData);
    vol.setData(volData);
    const bars = priceData.length;
    barCountRef.current = bars;
    const ts = chartRef.current.timeScale();
    if (bars > 0) {
      if (fitKeyRef.current !== fitKey) {
        // New pair/timeframe/type: anchor to the most recent DEFAULT_VIEW candles
        // (not the whole history). The price axis auto-scales to this window.
        const view = Math.min(bars, DEFAULT_VIEW);
        ts.setVisibleLogicalRange({ from: bars - view, to: bars });
        fitKeyRef.current = fitKey;
        followingRef.current = true;
      } else if (followingRef.current) {
        // Live refresh while pinned to the newest bar: advance the window to include
        // new candles, preserving the viewer's current zoom width.
        const range = ts.getVisibleLogicalRange();
        const width = range ? Math.max(2, range.to - range.from) : DEFAULT_VIEW;
        ts.setVisibleLogicalRange({ from: bars - width, to: bars });
      }
      // If the viewer scrolled back to older data, leave their view untouched.
    }
    requestAnimationFrame(positionDivider);
  }

  function positionDivider() {
    const chart = chartRef.current;
    const band = bandRef.current;
    const line = lineRef.current;
    if (!chart || !band || !line) return;
    if (realBoundary == null) {
      band.style.display = 'none';
      line.style.display = 'none';
      return;
    }
    const x = chart.timeScale().timeToCoordinate(realBoundary);
    if (x == null || x <= 0) {
      band.style.display = 'none';
      line.style.display = 'none';
      return;
    }
    band.style.display = 'block';
    band.style.width = `${x}px`;
    line.style.display = 'block';
    line.style.left = `${x}px`;
  }

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <div ref={containerRef} style={{ position: 'absolute', inset: 0 }} />
      {/* Seeded region shading + LIVE divider, positioned from the real boundary. */}
      <div
        ref={bandRef}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          bottom: 26,
          background: 'rgba(120,130,150,0.045)',
          pointerEvents: 'none',
          display: 'none',
        }}
      />
      <div
        ref={lineRef}
        style={{
          position: 'absolute',
          top: 0,
          bottom: 26,
          borderLeft: `1.2px dashed ${C.amber}`,
          pointerEvents: 'none',
          display: 'none',
        }}
      >
        <span
          style={{
            position: 'absolute',
            top: 4,
            right: 4,
            fontSize: 9.5,
            letterSpacing: '.08em',
            color: C.muted2,
            fontFamily: SANS,
          }}
        >
          SEEDED
        </span>
        <span
          style={{
            position: 'absolute',
            top: 4,
            left: 4,
            fontSize: 9.5,
            letterSpacing: '.08em',
            color: C.amber,
            fontFamily: SANS,
            fontWeight: 600,
          }}
        >
          LIVE
        </span>
      </div>
    </div>
  );
}

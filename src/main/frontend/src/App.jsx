import { useRoute } from './router.jsx';
import { MarketsPage } from './pages/MarketsPage.jsx';
import { TerminalPage } from './pages/TerminalPage.jsx';

export function App() {
  const path = useRoute();

  // /trade/:pair -> terminal; everything else -> markets landing.
  const tradeMatch = path.match(/^\/trade\/([^/]+)$/);
  if (tradeMatch) {
    return <TerminalPage pair={decodeURIComponent(tradeMatch[1])} />;
  }
  return <MarketsPage />;
}

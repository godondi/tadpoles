// TEST ONLY: explicitly launched by isolated verification/recovery scripts.
// Never selected by application configuration or used as a provider fallback.
import { createServer } from "node:http";
const symbols = new Set([
  "SPY",
  "AAPL",
  "MSFT",
  "AMZN",
  "GOOGL",
  "TSLA",
  "VOO",
  "AGG",
  "JNJ",
  "PG",
  "NVDA",
  "JPM",
  "UNH",
  "XOM",
  "BRK.B",
]);
createServer((request, response) => {
  const url = new URL(request.url, "http://test.invalid");
  const [, resource, symbol] = url.pathname.split("/");
  response.setHeader("Content-Type", "application/json");
  const fail = (status, message) => {
    response.statusCode = status;
    response.end(JSON.stringify({ error: { message } }));
  };
  if (request.headers["x-api-key"] !== "isolated-test-key")
    return fail(401, "Test provider key missing");
  if (!symbols.has(symbol)) return fail(404, "Unknown test symbol");
  const asOf = new Date().toISOString();
  const meta = {
    symbol,
    asOf,
    source: "isolated HTTP test fixture",
    disclaimer: "Test fixture only. Not live market data.",
    stale: false,
    spreadSource: "modelled",
  };
  if (resource === "quotes")
    return response.end(
      JSON.stringify({
        data: {
          symbol,
          currency: "USD",
          price: 100,
          bid: 99,
          ask: 101,
          spreadBps: 200,
          previousClose: 98,
          change: 2,
          changePercent: 2.04,
          asOf,
          marketState: "open",
        },
        meta,
      }),
    );
  if (resource === "candles") {
    if (url.searchParams.get("interval") !== "1d")
      return fail(400, "Daily only");
    const end = new Date(url.searchParams.get("to") || asOf.slice(0, 10));
    const start = new Date(
      url.searchParams.get("from") ||
        new Date(end - 30 * 86400000).toISOString().slice(0, 10),
    );
    const candles = [];
    for (
      let date = new Date(start);
      date <= end && candles.length < 400;
      date.setUTCDate(date.getUTCDate() + 1)
    ) {
      if ([0, 6].includes(date.getUTCDay())) continue;
      candles.push({
        date: date.toISOString().slice(0, 10),
        open: 98,
        high: 102,
        low: 97,
        close: 100,
        adjclose: 100,
        volume: 1000,
        synthetic: true,
      });
    }
    return response.end(
      JSON.stringify({
        data: { symbol, interval: "1d", currency: "USD", candles },
        meta: { ...meta, partial: false, synthetic: true },
      }),
    );
  }
  fail(404, "Unknown test route");
}).listen(8080, "0.0.0.0");

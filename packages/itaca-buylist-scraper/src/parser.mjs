const CARD_START = /<div\s+class=["'][^"']*\bcajaproducto\b[^"']*["'][^>]*>/gi;

function decodeHtml(value) {
  return String(value ?? "")
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCodePoint(Number.parseInt(code, 16)))
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number.parseInt(code, 10)))
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, "\"")
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">");
}

function text(value) {
  return decodeHtml(String(value ?? "").replace(/<[^>]*>/g, " "))
    .replace(/\s+/g, " ")
    .trim();
}

function capture(source, expression, group = 1) {
  return expression.exec(source)?.[group] ?? "";
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function parseEuro(value) {
  const normalized = value.includes(",")
    ? value.replace(/\./g, "").replace(",", ".")
    : value;
  const amount = Number.parseFloat(normalized);
  return Number.isFinite(amount) ? amount : null;
}

function absoluteUrl(value, baseUrl) {
  if (!value) return "";
  try {
    return new URL(decodeHtml(value), baseUrl).href;
  } catch {
    return decodeHtml(value);
  }
}

function productChunks(html) {
  const starts = [...String(html).matchAll(CARD_START)];
  return starts.map((start, index) => {
    const from = start.index ?? 0;
    const to = starts[index + 1]?.index ?? html.length;
    return html.slice(from, to);
  });
}

function parseProductId(chunk) {
  return capture(chunk, /<form[^>]+id=["']buyListItemForm-(\d+)["']/i)
    || capture(chunk, /addToBuyListCart\(\s*(\d+)/i)
    || capture(chunk, /name=["']productId["'][^>]*value=["'](\d+)["']/i)
    || capture(chunk, /value=["'](\d+)["'][^>]*name=["']productId["']/i);
}

function parseExpansionLink(chunk, fallback) {
  const href = capture(
    chunk,
    /href=["']([^"']*(?:\/buylist\/expansion|\/buyListCollection\/filter)[^"']*)["']/i
  );
  if (!href) return fallback;
  try {
    const url = new URL(decodeHtml(href), "https://itaca.gg");
    return {
      code: url.searchParams.get("expansionAbbreviation")?.trim().toUpperCase() || fallback.code,
      name: url.searchParams.get("expansionName")?.trim() || fallback.name
    };
  } catch {
    return fallback;
  }
}

function idsForMenu(chunk, suffix) {
  const expression = new RegExp(`id=["']([A-Z_]+)-${suffix}-\\d+["']`, "g");
  return unique([...chunk.matchAll(expression)].map(match => match[1]));
}

export function parseBuylistPage(html, options = {}) {
  const baseUrl = options.baseUrl || "https://itaca.gg";
  const fallbackExpansion = {
    code: String(options.expansionCode || "").trim().toUpperCase(),
    name: String(options.expansionName || "").trim()
  };

  const cards = productChunks(String(html)).map(chunk => {
    const productId = parseProductId(chunk);
    const title = text(capture(
      chunk,
      /<span\s+class=["'][^"']*\btitulocartacaja\b[^"']*["'][^>]*>([\s\S]*?)<\/span>/i
    ));
    if (!productId || !title) return null;

    const localizedName = text(capture(
      chunk,
      /<span\s+class=["'][^"']*\bsubtitulocartacaja\b[^"']*["'][^>]*>([\s\S]*?)<\/span>/i
    )).replace(/^\((.*)\)$/s, "$1");
    const imagePath = capture(
      chunk,
      /<img[^>]+class=["'][^"']*\bimagencarta\b[^"']*["'][^>]+src=["']([^"']+)["']/i
    ) || capture(
      chunk,
      /<img[^>]+src=["']([^"']+)["'][^>]+class=["'][^"']*\bimagencarta\b[^"']*["']/i
    );
    const cardmarketId = capture(imagePath, /\/magic-single\/(\d+)\.[a-z0-9]+(?:[?#]|$)/i);
    const expansion = parseExpansionLink(chunk, fallbackExpansion);
    const prices = [...chunk.matchAll(
      /<div\s+class=["'][^"']*\bvalortabla\b[^"']*\bpreciotucarta\b[^"']*["'][^>]*>\s*([0-9][0-9.,]*)\s*€/gi
    )].map(match => parseEuro(match[1])).filter(value => value !== null);

    const defaultLanguage = capture(
      chunk,
      new RegExp(`id=["']visible-idioma-${productId}["'][\\s\\S]*?<img[^>]+alt=["']([^"']+)["']`, "i")
    );
    const defaultCondition = capture(
      chunk,
      new RegExp(`id=["']visible-estado-${productId}["'][\\s\\S]*?<img[^>]+alt=["']([^"']+)["']`, "i")
    );
    const selectedFinish = capture(
      chunk,
      new RegExp(`id=["']card-type-${productId}["'][^>]*data-card-type=["']([^"']+)["']`, "i")
    );
    const selectedFinishLabel = capture(
      chunk,
      new RegExp(`id=["']visible-tipo-${productId}["'][\\s\\S]*?<img[^>]+alt=["']([^"']+)["']`, "i")
    ).toLowerCase();
    const defaultFinish = selectedFinish
      || (selectedFinishLabel.startsWith("no ") ? "REGULAR" : selectedFinishLabel.includes("foil") ? "FOIL" : "");

    return {
      productId,
      cardmarketId: cardmarketId || null,
      name: title,
      localizedName: localizedName || null,
      expansionCode: expansion.code,
      expansionName: expansion.name,
      imageUrl: absoluteUrl(imagePath, baseUrl),
      availableLanguages: idsForMenu(chunk, "idioma"),
      availableConditions: idsForMenu(chunk, "estado"),
      availableFinishes: idsForMenu(chunk, "tipo"),
      defaultSelection: {
        language: decodeHtml(defaultLanguage) || null,
        condition: defaultCondition || null,
        finish: defaultFinish || null
      },
      defaultOffer: {
        cashEur: prices[0] ?? null,
        itacoins: prices[1] ?? null
      }
    };
  }).filter(Boolean);

  return {
    cards,
    nextOffset: followingOffset(html, options.currentOffset ?? 0)
  };
}

export function followingOffset(html, currentOffset = 0) {
  const offsets = [...String(html).matchAll(/[?&](?:amp;)?offset=(\d+)/gi)]
    .map(match => Number.parseInt(match[1], 10))
    .filter(value => Number.isInteger(value) && value > currentOffset);
  return offsets.length ? Math.min(...offsets) : null;
}

export function parseExpansionDirectory(html) {
  const result = new Map();
  const links = String(html).matchAll(
    /<a[^>]+href=["']([^"']*(?:\/buylist\/expansion|\/buyListCollection\/filter)[^"']*)["'][^>]*>([\s\S]*?)<\/a>/gi
  );
  for (const match of links) {
    try {
      const url = new URL(decodeHtml(match[1]), "https://itaca.gg");
      const code = url.searchParams.get("expansionAbbreviation")?.trim().toUpperCase();
      const name = url.searchParams.get("expansionName")?.trim() || text(match[2]);
      if (code && name && !result.has(code)) result.set(code, { code, name });
    } catch {
      // Ignore malformed third-party links and keep parsing the directory.
    }
  }
  return [...result.values()];
}

export function extractOwnedSetCodes(value) {
  const result = new Set();
  const visit = node => {
    if (Array.isArray(node)) {
      node.forEach(visit);
      return;
    }
    if (!node || typeof node !== "object") return;
    for (const [key, child] of Object.entries(node)) {
      if (key === "setCode" && typeof child === "string" && child.trim()) {
        result.add(child.trim().toUpperCase());
      } else {
        visit(child);
      }
    }
  };
  visit(value);
  return [...result].sort();
}

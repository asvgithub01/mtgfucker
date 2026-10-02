import assert from "node:assert/strict";
import test from "node:test";
import {
  extractOwnedSetCodes,
  parseBuylistPage,
  parseExpansionDirectory
} from "../src/parser.mjs";

const CARD_HTML = `
<div class="cajaproducto impar">
  <img src="https://itaca.gg/img/product/magic-single/883000.jpg" class="imagencarta">
  <div class="cajainfocarta">
    <span class="titulocartacaja">Emeritus of Woe // Demonic Tutor</span>
    <span class="subtitulocartacaja">(Emérita de la miseria // Tutor demoníaco)</span>
    <span class="expansioncartacaja">
      <a href="/buylist/expansion?expansionName=Secrets+of+Strixhaven&amp;expansionAbbreviation=SOS">Secrets of Strixhaven</a>
    </span>
  </div>
  <form id="buyListItemForm-144387" onsubmit="addToBuyListCart(144387, '/buyOrder/addArticle'); return false;">
    <span id="ENGLISH-idioma-144387"></span><span id="SPANISH-idioma-144387"></span>
    <div id="visible-idioma-144387"><img alt="Inglés"></div>
    <span id="NM-estado-144387"></span><span id="EX-estado-144387"></span>
    <div id="visible-estado-144387"><img alt="NM"></div>
    <span id="card-type-144387" data-card-type="REGULAR">No foil</span>
    <span id="FOIL-tipo-144387"></span><span id="REGULAR-tipo-144387"></span>
    <div class="valortabla preciotucarta">8,50 €</div>
    <div class="valortabla preciototal">8,50 €</div>
    <div class="valortabla preciotucarta">10,19 €</div>
    <div class="valortabla preciototal">10,19 €</div>
    <input name="productId" type="hidden">
  </form>
</div>
<a title="Secrets of Strixhaven - Next page" href="/buylist/expansion?expansionAbbreviation=SOS&amp;max=20&amp;offset=20"></a>`;

test("parsea el HTML actual aunque productId no tenga value", () => {
  const page = parseBuylistPage(CARD_HTML, { currentOffset: 0 });
  assert.equal(page.cards.length, 1);
  assert.deepEqual(page.cards[0], {
    productId: "144387",
    cardmarketId: "883000",
    name: "Emeritus of Woe // Demonic Tutor",
    localizedName: "Emérita de la miseria // Tutor demoníaco",
    expansionCode: "SOS",
    expansionName: "Secrets of Strixhaven",
    imageUrl: "https://itaca.gg/img/product/magic-single/883000.jpg",
    availableLanguages: ["ENGLISH", "SPANISH"],
    availableConditions: ["NM", "EX"],
    availableFinishes: ["FOIL", "REGULAR"],
    defaultSelection: { language: "Inglés", condition: "NM", finish: "REGULAR" },
    defaultOffer: { cashEur: 8.5, itacoins: 10.19 }
  });
  assert.equal(page.nextOffset, 20);
});

test("interpreta No foil como acabado regular cuando falta data-card-type", () => {
  const html = CARD_HTML
    .replace(' id="card-type-144387" data-card-type="REGULAR"', ' id="card-type-144387"')
    .replace('<span id="FOIL-tipo-144387"></span>', '<div id="visible-tipo-144387"><img alt="No foil"></div><span id="FOIL-tipo-144387"></span>');
  assert.equal(parseBuylistPage(html).cards[0].defaultSelection.finish, "REGULAR");
});

test("extrae y deduplica el índice de expansiones", () => {
  const html = `
    <a href="/buylist/expansion?expansionName=Secrets+of+Strixhaven&amp;expansionAbbreviation=SOS">SOS</a>
    <a href="/buyListCollection/filter?expansionAbbreviation=STX&amp;expansionName=Strixhaven%3A+School+of+Mages">STX</a>
    <a href="/buylist/expansion?expansionName=Secrets+of+Strixhaven&amp;expansionAbbreviation=SOS">repetida</a>`;
  assert.deepEqual(parseExpansionDirectory(html), [
    { code: "SOS", name: "Secrets of Strixhaven" },
    { code: "STX", name: "Strixhaven: School of Mages" }
  ]);
});

test("encuentra setCode en una o varias Biblios exportadas", () => {
  assert.deepEqual(extractOwnedSetCodes({ libraries: [
    { cards: [{ setCode: "stx" }, { setCode: "SOS" }] },
    { cards: [{ setCode: "STX" }, { other: "ignored" }] }
  ] }), ["SOS", "STX"]);
});

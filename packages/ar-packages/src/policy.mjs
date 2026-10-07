import { createHash } from 'node:crypto';
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
export function validateSources(sources) {
  if (!sources?.length) throw Error('Empty coverage');
  for (const s of sources) {
    if (!/^[A-Z0-9]{2,8}$/.test(s.set)) throw Error('Invalid set');
    if (s.set === 'SPG' && !s.numbers?.length && !s.standalone) throw Error('SPG requires explicit collector numbers');
  }
}
export function selectCards(cards, source) {
  const selected=cards.filter(c=>c.games.includes('paper') && (!source.numbers || source.numbers.includes(c.collector_number)));
  if (source.numbers && source.numbers.some(n=>!selected.some(c=>c.collector_number===n))) throw Error('Incomplete annex');
  return selected;
}
export function validateReferences(refs) {
  if (!refs.length || refs.length>1000 || new Set(refs.map(c=>c.printing)).size!==refs.length) throw Error('Invalid reference count/duplicates (1–1000)');
}

/** Minimal RFC-4180 CSV reader/writer (no dependencies). */

/** Parses CSV text into an array of rows (arrays of strings). Blank lines are skipped. */
function parseRows(input) {
  let text = String(input);
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1); // strip BOM (Excel exports)

  const rows = [];
  let row = [];
  let field = '';
  let inQuotes = false;
  let quoted = false;

  const endField = () => {
    row.push(field);
    field = '';
    quoted = false;
  };
  const endRow = () => {
    endField();
    const blank = row.length === 1 && row[0] === '' && !quoted;
    if (!blank) rows.push(row);
    row = [];
  };

  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += c;
      }
    } else if (c === '"' && field === '' && !quoted) {
      inQuotes = true;
      quoted = true;
    } else if (c === ',') {
      endField();
    } else if (c === '\r' || c === '\n') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      // an empty line has no field content; endRow() drops it
      const wasQuoted = quoted;
      row.push(field);
      const isBlank = row.length === 1 && row[0] === '' && !wasQuoted;
      if (!isBlank) rows.push(row);
      row = [];
      field = '';
      quoted = false;
    } else {
      field += c;
    }
  }
  if (inQuotes) throw new Error('Malformed CSV: unterminated quoted field');
  if (field !== '' || row.length > 0 || quoted) endRow();
  return rows;
}

/**
 * Parses CSV with a header row. Header matching is case-insensitive and values are trimmed.
 * Returns an array of records exposing get(column) / isSet(column).
 */
function parseRecords(input) {
  const rows = parseRows(input);
  if (rows.length === 0) return [];
  const headerIndex = new Map();
  rows[0].forEach((name, idx) => {
    const key = name.trim().toLowerCase();
    if (key && !headerIndex.has(key)) headerIndex.set(key, idx);
  });
  return rows.slice(1).map((cells) => ({
    isSet(column) {
      const idx = headerIndex.get(column.toLowerCase());
      return idx !== undefined && idx < cells.length;
    },
    get(column) {
      const idx = headerIndex.get(column.toLowerCase());
      if (idx === undefined || idx >= cells.length) return null;
      return cells[idx].trim();
    },
  }));
}

function formatField(value) {
  if (value === null || value === undefined) return '';
  const s = String(value);
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** Serialises rows (arrays of values) to CSV text. */
function toCsv(rows, eol = '\r\n') {
  return rows.map((r) => r.map(formatField).join(',')).join(eol) + (rows.length ? eol : '');
}

module.exports = { parseRows, parseRecords, toCsv };

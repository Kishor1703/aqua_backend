/**
 * Dependency-free single-page PDF (Helvetica, ASCII only) - same approach the
 * Java version used. First line is rendered as a title.
 */
function escapePdfText(value) {
  return String(value)
    .replace(/[^\x20-\x7E]/g, '?')
    .replace(/\\/g, '\\\\')
    .replace(/\(/g, '\\(')
    .replace(/\)/g, '\\)');
}

function buildSimplePdf(lines) {
  const safe = lines.map(escapePdfText);
  let content = `BT\n/F1 16 Tf\n50 760 Td\n(${safe[0]}) Tj\n/F1 11 Tf\n0 -24 Td\n`;
  for (let i = 1; i < safe.length; i++) content += `(${safe[i]}) Tj\n0 -16 Td\n`;
  content += 'ET';
  const contentLength = Buffer.byteLength(content, 'latin1');

  const chunks = [];
  const offsets = [];
  let size = 0;
  const write = (text) => {
    const buf = Buffer.from(text, 'latin1');
    chunks.push(buf);
    size += buf.length;
  };
  const object = (text) => {
    offsets.push(size);
    write(text);
  };

  write('%PDF-1.4\n');
  object('1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n');
  object('2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n');
  object('3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n');
  object(`4 0 obj << /Length ${contentLength} >> stream\n${content}\nendstream endobj\n`);
  object('5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n');

  const xrefStart = size;
  write('xref\n0 6\n0000000000 65535 f \n');
  for (const offset of offsets) write(`${String(offset).padStart(10, '0')} 00000 n \n`);
  write(`trailer << /Size 6 /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`);
  return Buffer.concat(chunks);
}

module.exports = { buildSimplePdf };

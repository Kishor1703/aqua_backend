/**
 * Spring-Data-compatible paging: ?page=0&size=20&sort=name,desc
 * Sort properties are whitelisted and mapped to SQL columns (never interpolated from input).
 */
const { BadRequestError } = require('./errors');

const first = (v) => (Array.isArray(v) ? v[0] : v);
const toInt = (v) => (v !== undefined && /^-?\d+$/.test(String(v).trim()) ? Number(v) : null);

function parseSort(sortParam) {
  const entries = [];
  for (const raw of [].concat(sortParam ?? [])) {
    const tokens = String(raw).split(',').map((t) => t.trim()).filter(Boolean);
    if (tokens.length === 0) continue;
    const last = tokens[tokens.length - 1].toLowerCase();
    const hasDir = tokens.length > 1 && (last === 'asc' || last === 'desc');
    const direction = hasDir ? last : 'asc';
    for (const prop of hasDir ? tokens.slice(0, -1) : tokens) entries.push([prop, direction]);
  }
  return entries;
}

/**
 * @param query    req.query
 * @param opts.defaultSize
 * @param opts.defaultSort  [[prop, 'asc'|'desc'], ...]
 * @param opts.sortable     { apiProperty: 'sql.column' }
 * @param opts.tieBreak     final ORDER BY term for stable paging, e.g. 'a.id ASC'
 */
function parsePageable(query, { defaultSize = 20, defaultSort, sortable, tieBreak, maxSize = 2000 }) {
  const page = toInt(first(query.page));
  const size = toInt(first(query.size));
  const pageNumber = page !== null && page >= 0 ? page : 0;
  const pageSize = size !== null && size >= 1 ? Math.min(size, maxSize) : defaultSize;

  let sort = parseSort(query.sort);
  if (sort.length === 0) sort = defaultSort;

  const terms = sort.map(([prop, dir]) => {
    if (!Object.prototype.hasOwnProperty.call(sortable, prop)) {
      throw new BadRequestError(`Unsupported sort property: ${prop}`);
    }
    return `${sortable[prop]} ${dir.toUpperCase()}`;
  });
  if (tieBreak) terms.push(tieBreak);

  return { page: pageNumber, size: pageSize, offset: pageNumber * pageSize, orderBy: terms.join(', ') };
}

function pagedResponse(content, totalElements, pageable) {
  const totalPages = Math.ceil(totalElements / pageable.size);
  return {
    content,
    pageNumber: pageable.page,
    pageSize: pageable.size,
    totalElements,
    totalPages,
    last: pageable.page + 1 >= totalPages,
  };
}

module.exports = { parsePageable, pagedResponse };

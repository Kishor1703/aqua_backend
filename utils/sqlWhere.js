/** Builds a parameterised WHERE clause. Values always go through $n placeholders. */
class Where {
  constructor() {
    this.clauses = [];
    this.params = [];
  }

  /** Registers a value and returns its placeholder, e.g. '$3'. */
  param(value) {
    this.params.push(value);
    return `$${this.params.length}`;
  }

  add(clause) {
    this.clauses.push(clause);
    return this;
  }

  sql() {
    return this.clauses.length ? `WHERE ${this.clauses.join(' AND ')}` : '';
  }
}

/** Escapes LIKE wildcards so user input is matched literally. */
const escapeLike = (s) => String(s).replace(/[\\%_]/g, '\\$&');

module.exports = { Where, escapeLike };

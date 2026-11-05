class AppError extends Error {
  constructor(status, message, fieldErrors = []) {
    super(message);
    this.name = this.constructor.name;
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

class NotFoundError extends AppError {
  constructor(message) { super(404, message); }
}
class DuplicateResourceError extends AppError {
  constructor(message) { super(409, message); }
}
class InvalidStateTransitionError extends AppError {
  constructor(message) { super(409, message); }
}
/** Equivalent of Java's IllegalArgumentException -> 400. */
class BadRequestError extends AppError {
  constructor(message) { super(400, message); }
}
class ValidationError extends AppError {
  constructor(fieldErrors) {
    super(400, 'One or more fields are invalid', fieldErrors);
    this.label = 'Validation Failed';
  }
}
class UnauthorizedError extends AppError {
  constructor(message = 'Authentication required') { super(401, message); }
}
/** Deliberately vague - never reveal whether the email or the password was wrong. */
class InvalidCredentialsError extends UnauthorizedError {
  constructor() { super('Invalid email or password'); }
}
class ForbiddenError extends AppError {
  constructor(message = 'You do not have permission to perform this action') { super(403, message); }
}

module.exports = {
  AppError,
  NotFoundError,
  DuplicateResourceError,
  InvalidStateTransitionError,
  BadRequestError,
  ValidationError,
  UnauthorizedError,
  InvalidCredentialsError,
  ForbiddenError,
};

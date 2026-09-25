'use strict';

/**
 * utils/asyncHandler.js
 * Membalut async controller supaya sebarang rejection diteruskan
 * ke error middleware Express (mengelakkan unhandled promise rejection).
 *
 * @param {Function} fn - Express async handler (req, res, next)
 * @returns {Function}
 */
function asyncHandler(fn) {
  return function wrappedHandler(req, res, next) {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

module.exports = { asyncHandler };

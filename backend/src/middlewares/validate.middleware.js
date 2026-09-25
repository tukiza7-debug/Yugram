'use strict';

/**
 * middlewares/validate.middleware.js
 * Middleware pengesahan Joi generik untuk body/params/query Express.
 */

const ApiError = require('../utils/apiError');

/**
 * @param {import('joi').ObjectSchema} schema
 * @param {'body'|'params'|'query'} [property]
 * @returns {Function}
 */
function validate(schema, property = 'body') {
  return function validateMiddleware(req, res, next) {
    const target = property === 'query' ? { ...req.query } : req[property];
    const { value, error } = schema.validate(target ?? {}, {
      abortEarly: false,
      stripUnknown: true,
      convert: true,
    });
    if (error) {
      next(
        ApiError.badRequest(
          'Payload tidak sah',
          error.details.map((detail) => ({
            field: detail.path.join('.'),
            message: detail.message,
          }))
        )
      );
      return;
    }
    if (property === 'query') {
      Object.defineProperty(req, 'validatedQuery', { value, enumerable: true });
    } else {
      req[property] = value;
    }
    next();
  };
}

module.exports = { validate };

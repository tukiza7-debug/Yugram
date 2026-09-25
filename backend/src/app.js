'use strict';

/**
 * app.js
 * Aplikasi Express: middleware global, laluan API dan pengendali ralat.
 */

const express = require('express');
const cors = require('cors');
const config = require('./config/env');
const logger = require('./utils/logger');
const routes = require('./routes');
const { UPLOAD_DIR } = require('./middlewares/upload.middleware');
const { notFoundHandler, errorHandler } = require('./middlewares/error.middleware');

const log = logger.child('HTTP');

const app = express();

app.disable('x-powered-by');
app.use(
  cors({
    origin: config.corsOrigins,
    credentials: true,
  })
);
app.use(express.json({ limit: '256kb' }));

// Logger permintaan ringkas (peringkat debug).
app.use((req, res, next) => {
  const startedAt = process.hrtime.bigint();
  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - startedAt) / 1e6;
    log.debug('Permintaan HTTP', {
      method: req.method,
      path: req.originalUrl,
      status: res.statusCode,
      durationMs: Math.round(durationMs * 100) / 100,
    });
  });
  next();
});

// Health check - dipakai load balancer / monitoring.
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'ok',
    uptime: process.uptime(),
    timestamp: new Date().toISOString(),
  });
});

// FASA 2: fail media yang dimuat naik dihidang statik (nama cakera rawak).
app.use('/uploads', express.static(UPLOAD_DIR, {
  maxAge: '30d',
  immutable: true,
}));

app.use('/api', routes);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;

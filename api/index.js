const { createApp } = require('../app');

// Vercel invokes this Express app as a serverless function. Local development
// continues to use `node index.js`, which also runs migrations and the scheduler.
module.exports = createApp();

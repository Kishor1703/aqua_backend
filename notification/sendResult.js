const ok = (providerMessageId = null) => ({ success: true, providerMessageId, errorMessage: null });
const failure = (errorMessage) => ({ success: false, providerMessageId: null, errorMessage });
module.exports = { ok, failure };

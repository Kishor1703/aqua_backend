const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');

// Stub the DB pool and dispatcher so the scheduler logic runs without PostgreSQL.
const poolPath = require.resolve('../src/db/pool');
const dispatchPath = require.resolve('../src/services/notificationDispatchService');
const state = { schedules: [], claimed: new Set(), dispatched: [], loseRace: false };

require.cache[poolPath] = {
  id: poolPath, filename: poolPath, loaded: true,
  exports: {
    query: async (sql, params) => {
      if (/SELECT id, release_date/.test(sql)) return { rows: state.schedules.filter((s) => !state.claimed.has(s.id)) };
      if (/UPDATE water_schedules SET notification_triggered_at/.test(sql)) {
        const id = params[0];
        if (state.loseRace) return { rowCount: 0 };
        if (state.claimed.has(id)) return { rowCount: 0 };
        state.claimed.add(id);
        return { rowCount: 1 };
      }
      throw new Error(`unexpected sql: ${sql}`);
    },
  },
};
require.cache[dispatchPath] = {
  id: dispatchPath, filename: dispatchPath, loaded: true,
  exports: { dispatchForSchedule: async (id) => { state.dispatched.push(id); } },
};

const { NotificationScheduler } = require('../src/scheduler/notificationScheduler');

test('only schedules whose notify-at has arrived are dispatched, and only once', async () => {
  const now = new Date(2026, 10, 5, 9, 0, 0); // 09:00 local
  state.schedules = [
    { id: 1, release_date: '2026-11-05', release_time: '10:00:00', notify_before_mins: 60 },  // notify 09:00 -> due
    { id: 2, release_date: '2026-11-05', release_time: '10:30:00', notify_before_mins: 60 },  // notify 09:30 -> not yet
    { id: 3, release_date: '2026-11-04', release_time: '23:00:00', notify_before_mins: 30 },  // overdue -> due
  ];
  const job = new NotificationScheduler(60000);

  assert.equal(await job.checkAndDispatchDueSchedules(now), 2);
  await job.stop(100);
  assert.deepEqual(state.dispatched.sort(), [1, 3]);

  // Second poll at the same time must not re-fire anything.
  assert.equal(await job.checkAndDispatchDueSchedules(now), 0);
  // Half an hour later schedule 2 becomes due.
  assert.equal(await job.checkAndDispatchDueSchedules(new Date(2026, 10, 5, 9, 30, 0)), 1);
  await job.stop(100);
  assert.deepEqual(state.dispatched.sort(), [1, 2, 3]);
});

test('a lost claim race (another instance won) dispatches nothing', async () => {
  state.schedules = [{ id: 9, release_date: '2026-11-05', release_time: '08:00:00', notify_before_mins: 5 }];
  state.dispatched = [];
  state.claimed = new Set();
  state.loseRace = true; // SELECT sees it unclaimed, but the UPDATE finds it already claimed elsewhere
  const job = new NotificationScheduler(60000);
  assert.equal(await job.checkAndDispatchDueSchedules(new Date(2026, 10, 5, 9, 0, 0)), 0);
  assert.deepEqual(state.dispatched, []);
});

const assert = require('node:assert/strict');
const test = require('node:test');

const {
  latestCodeRabbitCompletion,
} = require('./codex-review-gate');

test('latestCodeRabbitCompletion ignores nullish check runs', () => {
  const result = latestCodeRabbitCompletion([
    null,
    {
      name: 'CodeRabbit',
      conclusion: 'success',
      completed_at: '2026-03-27T16:03:41Z'
    }
  ]);

  assert.equal(result?.name, 'CodeRabbit');
  assert.equal(result?.completed_at, '2026-03-27T16:03:41Z');
});

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const assert = require('node:assert/strict');
const test = require('node:test');

const {
  latestCodeRabbitCompletion,
  publishCodexReviewGateHeadStatus,
  resolveCodeRabbitCompletedAt,
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

test('resolveCodeRabbitCompletedAt uses status context timestamp when REST lacks CodeRabbit', () => {
  const result = resolveCodeRabbitCompletedAt(
    [
      {
        name: 'Server Build (JDK 17)',
        conclusion: 'success',
        completed_at: '2026-03-27T17:13:59Z'
      }
    ],
    [
      {
        __typename: 'StatusContext',
        context: 'CodeRabbit',
        state: 'SUCCESS',
        createdAt: '2026-03-27T17:14:10Z'
      }
    ]
  );

  assert.equal(result, Date.parse('2026-03-27T17:14:10Z'));
});

test('resolveCodeRabbitCompletedAt uses GraphQL check run timestamp when available', () => {
  const result = resolveCodeRabbitCompletedAt(
    [
      {
        name: 'Server Build (JDK 17)',
        conclusion: 'success',
        completed_at: '2026-03-27T17:13:59Z'
      }
    ],
    [
      {
        __typename: 'CheckRun',
        name: 'CodeRabbit',
        conclusion: 'SUCCESS',
        completedAt: '2026-03-27T17:14:10Z'
      }
    ]
  );

  assert.equal(result, Date.parse('2026-03-27T17:14:10Z'));
});

test('publishCodexReviewGateHeadStatus writes a success status on the PR head', async () => {
  const calls = [];
  const github = {
    rest: {
      repos: {
        createCommitStatus: async (args) => {
          calls.push(args);
          return args;
        }
      }
    }
  };

  await publishCodexReviewGateHeadStatus(github, {
    owner: 'vega113',
    repo: 'incubator-wave',
    sha: 'bae2f90912a783403a3dad03d9e36fc41f851832',
    state: 'success',
    description: 'Codex Review Gate passed via PR comment reevaluation'
  });

  assert.deepEqual(calls, [
    {
      owner: 'vega113',
      repo: 'incubator-wave',
      sha: 'bae2f90912a783403a3dad03d9e36fc41f851832',
      state: 'success',
      context: 'Codex Review Gate',
      description: 'Codex Review Gate passed via PR comment reevaluation'
    }
  ]);
});

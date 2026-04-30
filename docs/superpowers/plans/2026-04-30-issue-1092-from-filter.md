# Issue #1092: Server-Side `from:` Search Filter

## Context

`from:me` is already emitted by the J2CL rail filter chip and parsed into
`TokenQueryType.FROM`, but the server currently treats it as a no-op. Simple
search filters `creator:` at the wavelet level, while this issue requires
wave-level "started by" semantics: the conversation root wavelet creator must
match the normalized `from:` value.

## Acceptance Criteria

- `from:me` resolves to the current viewer address, not `me@<domain>`.
- `from:<address>` resolves to the literal address, lower-cased for matching.
- Bare non-`me` values append the current user's local domain.
- Simple search filters waves by conversation root wavelet creator after
  folder/tag/attachment filtering and before title/content/mentions/tasks/unread
  filtering.
- Solr-backed search mirrors the same behavior with an in-memory post-filter so
  Simple and Solr do not diverge.
- Solr pagination treats active `from:` filtering as a post-filter, fetching
  from offset zero and applying `startAt` after filtering.
- Unknown/no-match `from:` values return zero results, not a broader no-op set.
- `creator:` behavior remains unchanged.

## Implementation Plan

- Add `FromQueryNormalizer` in `org.waveprotocol.box.server.waveserver`, modeled
  after `MentionQueryNormalizer`, with `me`, explicit address, and bare local
  name normalization.
- Add a shared package-private `FromSearchFilter` that:
  - detects active `TokenQueryType.FROM` values;
  - normalizes values for the current user;
  - filters mutable `List<WaveViewData>` results by conversation root wavelet
    creator using case-insensitive canonical address comparison.
- Wire `SimpleSearchProviderImpl` to:
  - compute normalized `from:` values after query parsing;
  - apply the `FromSearchFilter` after `has:attachment` and before text-like
    filters;
  - include the filter count in the summary log.
- Wire `SolrSearchProviderImpl` to:
  - keep stripping `from:` from the Solr text query;
  - treat active `from:` as a post-filter for Solr offset calculations;
  - apply `FromSearchFilter` after materializing `resultsList`.
- Update tests:
  - `FromQueryNormalizerTest` for `me`, explicit address, bare local names, and
    invalid inputs.
  - `SimpleSearchProviderImplTest` for `in:inbox from:me`, literal address,
    bare local name, no-match behavior, and coexistence with `creator:`.
  - `SolrSearchProviderImplTest` for active-query detection, root-author
    filtering, no-match behavior, and Solr pagination helper behavior.
- Add a changelog fragment for the J2CL-visible search filter behavior.

## Verification Plan

- `sbt --batch 'Test / testOnly org.waveprotocol.box.server.waveserver.FromQueryNormalizerTest org.waveprotocol.box.server.waveserver.QueryHelperTest org.waveprotocol.box.server.waveserver.SolrSearchProviderImplTest org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'`
- `sbt --batch compile j2clSearchTest`
- `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
- `git diff --check origin/main...HEAD`

## Self-Review

- The plan keeps `from:` separate from `creator:` so existing wavelet-level
  `creator:` semantics are not silently changed.
- The plan makes Solr pagination post-filter aware; this avoids repeating the
  double-pagination issue fixed in #1091.
- The plan does not require UI changes because J-UI-2 already emits and
  round-trips the canonical token.
- The filter is shared between providers to reduce divergence while keeping
  provider-specific pagination and query-stripping behavior local.

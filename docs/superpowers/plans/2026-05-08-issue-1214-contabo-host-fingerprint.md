# Issue #1214 Contabo Host Key Fingerprint Plan

## Problem

The standalone `rollback-contabo.yml` workflow requires
`CONTABO_HOST_FINGERPRINT`, but production recovery showed the value is not
configured. The deploy workflow can still connect by appending an `ssh-keyscan`
result directly to `known_hosts`, so deploy and rollback currently use different
trust models.

## Plan

1. Add a focused contract test that fails while the deploy workflow lacks the
   pinned fingerprint guard and while the shared trust script is missing.
2. Add one shared `scripts/deployment/trust-ssh-host-key.sh` helper that:
   - rejects missing host or fingerprint configuration before network access,
   - scans the host key,
   - compares all scanned fingerprints against the pinned value, and
   - appends only the scanned host-key lines whose fingerprint matches.
3. Use that helper from both `deploy-contabo.yml` and `rollback-contabo.yml`.
4. Document the required `CONTABO_HOST_FINGERPRINT` repository variable or
   secret in the Contabo deployment overlay.
5. Add a changelog fragment because this changes production deploy and rollback
   operator behavior.
6. Verify with the focused Python tests, changelog validation, shell syntax
   checks, and whitespace checks before opening the PR.

## Self Review

- Scope is limited to GitHub Actions host-key trust and operator docs.
- The fix deliberately strengthens deploy to match rollback instead of weakening
  rollback to match deploy.
- The shared script reduces future drift between deploy and rollback workflows.
- No production workflow should be executed as part of local verification; this
  PR changes workflow behavior but should not trigger a real rollback.

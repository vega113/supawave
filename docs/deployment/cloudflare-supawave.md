# Cloudflare DNS for supawave.ai

Current status: blocked on zone onboarding.

## What I checked

I queried the Cloudflare API with the locally available token:

```bash
curl -sS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H 'Content-Type: application/json' \
  "https://api.cloudflare.com/client/v4/zones?name=supawave.ai&per_page=50"
```

Result:

- `success: true`
- `result: []`
- `total_count: 0`

That means `supawave.ai` is not present in the current Cloudflare account context.

## Consequence

No DNS records were created or changed.

## Minimal DNS target

The narrowest useful setup for Wave is:

- `A supawave.ai -> 86.48.3.138`

The `www` alias is optional and was not applied because the task asked for the narrowest
necessary setup.

## Exact next steps once the zone is available

1. Add `supawave.ai` to the Cloudflare account identified by
   `CLOUDFLARE_API_ACCOUNT_ID`.
2. Re-run `scripts/cloudflare/supawave-dns.sh` to confirm the zone appears.
3. Apply the apex record:

```bash
scripts/cloudflare/supawave-dns.sh --apply
```

If the zone still does not exist, create or import it first:

```bash
curl -sS -X POST "https://api.cloudflare.com/client/v4/zones" \
  -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"name":"supawave.ai","account":{"id":"'"$CLOUDFLARE_API_ACCOUNT_ID"'"},\
"jump_start":false}'
```

After the zone exists, the record creation API used by the script is:

```bash
curl -sS -X POST "https://api.cloudflare.com/client/v4/zones/$ZONE_ID/dns_records" \
  -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"type":"A","name":"supawave.ai","content":"86.48.3.138","ttl":1,"proxied":false}'
```

## Verification

After the record exists, verify it with:

```bash
dig +short supawave.ai
```

## External changes

None. The zone is absent in the accessible Cloudflare account, so no live DNS changes
were applied.

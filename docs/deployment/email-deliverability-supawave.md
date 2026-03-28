# SupaWave Email Deliverability Checklist

This note captures the current public DNS state for `supawave.ai` and the
most likely reasons mail sent through Resend is reaching spam.

Scope:
- outbound mail from SupaWave application flows
- public DNS records visible on 2026-03-28 UTC
- operator actions that must happen outside this repo

Non-goals:
- changing Cloudflare DNS from this worktree
- claiming mailbox-provider reputation results without mailbox-header samples

## Current public DNS comparison

Queried with:

```bash
for d in supawave.ai tube2web.ai; do
  echo "=== $d ==="
  for name in "$d" "_dmarc.$d" "resend._domainkey.$d" "send.$d"; do
    echo "-- $name"
    dig +short TXT "$name"
    dig +short MX "$name"
    dig +short CNAME "$name"
  done
done
```

Visible email-related records:

| Record | `supawave.ai` | `tube2web.ai` | Notes |
| --- | --- | --- | --- |
| apex TXT | `v=spf1 include:_spf.mx.cloudflare.net ~all` | same | This is the Cloudflare Email Routing SPF record, not the Resend sending SPF. |
| apex MX | `route1/2/3.mx.cloudflare.net` | same | Inbound mail is routed through Cloudflare on both domains. |
| `resend._domainkey` TXT | present | present | Resend DKIM key exists on both domains. |
| `send` TXT | `v=spf1 include:amazonses.com ~all` | same | Resend custom return-path SPF exists on both domains. |
| `send` MX | `10 feedback-smtp.us-east-1.amazonses.com.` | same | Resend custom return-path MX exists on both domains. |
| `_dmarc` TXT | missing | missing | Neither domain currently publishes DMARC. |

Conclusion:
- the visible Resend-related DNS on `supawave.ai` and `tube2web.ai` is effectively the same
- the spam problem is not explained by an obvious SPF or DKIM mismatch in the public records above
- the biggest visible authentication gap is the absence of DMARC

## Repo-side configuration findings

The deployment already sends application mail through Resend:

- `deploy/caddy/application.conf` sets `core.mail_provider = "resend"`
- `deploy/caddy/application.conf` reads `core.email_from_address = ${?WAVE_EMAIL_FROM}`
- `wave/config/reference.conf` exposes the same `WAVE_EMAIL_FROM` override

This means the sending address can be changed operationally without a code change once DNS is ready.

## Likely spam causes

### 1. No DMARC policy is published

This is the clearest DNS weakness.

Without DMARC:
- mailbox providers lose an alignment policy signal for `From:` mail
- domain owners receive no aggregate reports showing pass and fail behavior
- Gmail explicitly recommends DMARC in addition to SPF and DKIM

### 2. `supawave.ai` is an extremely new sending domain

WHOIS on 2026-03-28 shows:
- `supawave.ai` creation date: 2026-03-19
- `tube2web.ai` creation date: 2025-11-22

`supawave.ai` is still in the first days of sender reputation building. Even with correct SPF and DKIM, cold domains often hit spam until they accumulate positive engagement.

### 3. Mail is likely being sent from the root domain instead of a dedicated sending subdomain

Resend recommends a subdomain for sending so reputation is segmented by purpose.

For SupaWave that means:
- website reputation
- future human mailbox reputation
- automated auth-mail reputation

are all currently tied too closely together if `WAVE_EMAIL_FROM` uses `@supawave.ai`.

### 4. Auth-style traffic tends to look low-volume and low-engagement

Password resets, confirmations, and magic links are legitimate, but they are also:
- short-lived
- link-heavy
- often ignored by recipients who test the product once

That makes warm-up and domain trust more important than usual.

### 5. The sender presentation may be too bare

If `WAVE_EMAIL_FROM` is only `noreply@supawave.ai`, the mail lacks a recognizable display name. That is not the primary cause, but it is a weak trust signal compared with `SupaWave <auth@notify.supawave.ai>`.

## Recommended external DNS changes

### Minimum improvement set for the current domain

1. Publish DMARC at the apex:

```txt
Host: _dmarc
Type: TXT
Value: v=DMARC1; p=none; rua=mailto:dmarc@supawave.ai; adkim=r; aspf=r; pct=100
```

2. Make sure the reporting mailbox exists.

Recommended:
- create a Cloudflare Email Routing alias for `dmarc@supawave.ai`
- forward it to a monitored mailbox

3. After 1 to 2 weeks of clean aggregate reports, tighten the policy:

Phase 2:

```txt
Host: _dmarc
Type: TXT
Value: v=DMARC1; p=quarantine; rua=mailto:dmarc@supawave.ai; adkim=r; aspf=r; pct=100
```

Phase 3:

```txt
Host: _dmarc
Type: TXT
Value: v=DMARC1; p=reject; rua=mailto:dmarc@supawave.ai; adkim=r; aspf=r; pct=100
```

### Preferred long-term setup

Move automated application mail to a dedicated sending subdomain, for example `notify.supawave.ai`.

Create and verify that subdomain in Resend, then add the records Resend provides for that exact subdomain. With the default Resend naming pattern, expect records shaped like:

```txt
Host: resend._domainkey.notify
Type: TXT
Value: <Resend-provided DKIM public key>
```

```txt
Host: send.notify
Type: TXT
Value: v=spf1 include:amazonses.com ~all
```

```txt
Host: send.notify
Type: MX
Priority: 10
Value: feedback-smtp.us-east-1.amazonses.com.
```

Then update the runtime sender address to use that subdomain:

```bash
WAVE_EMAIL_FROM="SupaWave <auth@notify.supawave.ai>"
```

Why this is better:
- sender reputation is isolated from the apex domain
- future support or human mailboxes can stay on `supawave.ai`
- application auth mail gets its own warm-up path

## Recommended operational follow-up

1. Keep the existing `send.supawave.ai` and `resend._domainkey.supawave.ai` records in place until the new subdomain is verified and live.
2. Send only low-volume transactional mail first.
3. Seed a few trusted inboxes across Gmail and Outlook, then open, star, reply, and move messages out of spam if they land there.
4. Avoid batch sends from the new domain until engagement improves.
5. Use a branded sender string such as `SupaWave <auth@notify.supawave.ai>`.

## What does not currently look broken

Based on the public DNS checks above:
- Resend DKIM is present
- the Resend return-path SPF and MX records are present
- there is no visible MX conflict between Cloudflare inbound routing on the apex and Resend sending on the `send` subdomain

So the highest-confidence conclusion is:
- `supawave.ai` mail is probably hitting spam because the domain is brand new and lacks DMARC, not because the visible Resend DNS is missing

## Sources used for the recommendations

- Resend Cloudflare guide: https://resend.com/docs/knowledge-base/cloudflare
- Resend authentication guide: https://resend.com/blog/email-authentication-a-developers-guide
- Google authentication guidance: https://support.google.com/a/answer/10583557?hl=en

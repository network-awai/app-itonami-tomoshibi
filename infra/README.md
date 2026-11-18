# tomoshibi infra — mail worker + node residency

Design 正本: `../docs/adr/0002-mail-capability-and-murakumo-residency.md`
(doctrine boundary: itonami/root ADR-2607121830).

## mail-worker/ (Cloudflare Email Worker)

Inbound staging for tomoshibi@itonami.cloud. Deploy from `mail-worker/`:

```bash
npm install
npx wrangler deploy                      # account: the itonami.cloud zone account
npx wrangler secret put PULL_TOKEN      # value: 1Password gftdcojp `itonami.tomoshibi/PULL_TOKEN`
```

The Email Routing rule (tomoshibi@itonami.cloud → this Worker) is API-managed
(rule id `a5f16891e1744309950de630795a8a08`, zone 54dece4a…) — recreate with
`POST /zones/<zone>/email/routing/rules`, action `{"type":"worker","value":["tomoshibi-mail"]}`.

## Node deploy (murakumo fleet, current node: zebulun)

```bash
# 1. west-like layout so bb.edn's relative classpath works unchanged
mkdir -p ~/tomoshibi/orgs/{itonami,kotoba-lang}
cd ~/tomoshibi/orgs/itonami
git clone --depth 1 https://github.com/network-awai/app-itonami-tomoshibi.git
git clone --depth 1 --filter=blob:none --sparse https://github.com/etzhayyim/root.git
(cd root && git sparse-checkout set 20-actors/itonami-organism 00-contracts/lexicons/cloud/itonami/apps/itonami)
cd ../kotoba-lang
git clone --depth 1 https://github.com/kotoba-lang/mail.git
git clone --depth 1 https://github.com/kotoba-lang/mailer.git

# 2. verify offline suite ON THE NODE
cd ~/tomoshibi/orgs/itonami/app-itonami-tomoshibi && kbb run_tests.cljk

# 3. secrets (mode 600, NEVER committed) + leash
umask 077
cat > ~/.itonami/tomoshibi/env << 'EOF'
export TOMOSHIBI_PULL_URL=https://tomoshibi-mail.04-feasts-minded.workers.dev
export TOMOSHIBI_PULL_TOKEN=<1Password gftdcojp itonami.tomoshibi/PULL_TOKEN>
export RESEND_API_KEY=<1Password gftdcojp gftd.resend/API_KEY>
export ITONAMI_NODE_NAME=<node>
EOF
# leash: {:status "active" :granted-by "…" :at "…"} → ~/.itonami/tomoshibi/leash.edn
# (missing/malformed/revoked = agent does NOTHING — fail-closed)

# 4. one smoke tick, then the daemon
set -a; . ~/.itonami/tomoshibi/env; set +a
kbb -m tomoshibi.daemon --once
sed 's/@@NODE_USER@@/<node>/g' infra/launchd/cloud.itonami.tomoshibi.agent.plist.template \
  | sudo tee /Library/LaunchDaemons/cloud.itonami.tomoshibi.agent.plist > /dev/null
sudo chown root:wheel /Library/LaunchDaemons/cloud.itonami.tomoshibi.agent.plist
sudo launchctl bootstrap system /Library/LaunchDaemons/cloud.itonami.tomoshibi.agent.plist
curl -s http://127.0.0.1:13094/    # healthz (fleet-probe signature)
```

## Kill switches (any one suffices)

1. **Leash**: `~/.itonami/tomoshibi/leash.edn` — set `:status "revoked"` or
   delete; the agent checks it at the top of EVERY tick, fail-closed.
2. **Inbound**: disable Email Routing rule a5f16891… (mail stops arriving).
3. **Process**: `sudo launchctl bootout system/cloud.itonami.tomoshibi.agent`.

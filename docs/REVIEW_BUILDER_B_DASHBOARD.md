# Review — Builder B, session 1: Swing dashboard (HANDOFF §6, Phases 8–9)

> **For Builder A, and for Builder A's Claude.** This file explains everything Builder B changed on branch `builder-b` on 1 Oct 2026, how it was checked, and whether it can be pushed and merged.
> Read it together with `HANDOFF.md` (already updated: §2, §3 D18–D20, §4, §9) and `CONTRACT_SNAPSHOT.txt` (regenerated).
> §8 below is a short block to paste into your Claude session so it knows the new state.

---

## 1. Summary

- **New package `dbmonitor.client`** with the four classes named in v7 §6.3/§76 and HANDOFF §6: `ClientMain`, `AlertListenerThread`, `DashboardFrame`, `AlertCard`.
- **Build/run scripts:** `client\*.java` added to `build.cmd`/`build.sh`. New command `run client` / `sh run.sh client` starts the Swing dashboard. `run dashboard` is still the text-only `test/ConsoleDashboard`.
- **Docs:** README (run list, demo steps 1 and 5, one failure-demo line), HANDOFF (§2 Phase 8–9 rows, §3 D18–D20, §4 file list, §9 session row, header line), CONTRACT_SNAPSHOT (only the 4 new classes added).
- **Not touched:** `common/`, `rules/`, `db/`, `server/`, `admin/`, `employee/`, `test/`, `sql/`, `config/`. Checked with `git diff --name-only origin/main -- <those folders>` (empty).
- **No new dependencies. No schema change. No contract change** to any existing class.

## 2. Files changed

| File | Change |
|---|---|
| `src/dbmonitor/client/ClientMain.java` | **new**: entry point |
| `src/dbmonitor/client/AlertListenerThread.java` | **new**: network thread (connect / read / reconnect) |
| `src/dbmonitor/client/DashboardFrame.java` | **new**: window, id → card map, counters, status line |
| `src/dbmonitor/client/AlertCard.java` | **new**: one alert card |
| `build.cmd`, `build.sh` | + `src\dbmonitor\client\*.java` in the `javac` line |
| `run.cmd`, `run.sh` | + `client` command → `dbmonitor.client.ClientMain`; usage text updated |
| `CONTRACT_SNAPSHOT.txt` | regenerated with `javap`; diff vs. the old file = header date + 4 new classes only |
| `README.md` | `run client` in the run list and demo; RESOLVED step now describes the grey card; failure demo for Reconnecting |
| `HANDOFF.md` | §2, §3 (D18–D20), §4, §9, header |
| `docs/REVIEW_BUILDER_B_DASHBOARD.md` | **new**: this file |

All new sources are ASCII and CRLF, like the rest of the repo; `.gitattributes` normalises line endings.

## 3. Design: how the dashboard works

```
ClientMain (main thread)
  load config/app.properties  (missing -> [WARN] + AppConfig.defaults())
  set system look and feel
  invokeLater ->  EDT: new DashboardFrame, new AlertListenerThread, window-close hook, start listener

AlertListenerThread (one thread, never touches Swing)
  while running:
     invokeLater(showConnecting)
     new Socket(host, port) -> new ObjectInputStream      fresh socket + stream every attempt
     invokeLater(showConnected)
     loop: obj = readObject()
           if obj instanceof Alert -> invokeLater(frame.showAlert(alert))
           else [WARN] + skip
     on failure: invokeLater(showReconnecting(reason)); sleep client.reconnectMs; retry

DashboardFrame (EDT only)
  HashMap<Integer, AlertCard> cards       new id -> new card at top; known id -> card.update()
  recomputeCounters()                     EnumMap<Severity,Integer> rebuilt from ALL cards on every update

AlertCard (EDT only)
  only calls Alert methods: getIconText, getTypeLabel, getColor, getSeverity().getLabel()/getColor(),
  getId, getStatus, getDisplayMessage, getSource, getFormattedCreatedAt, isResolved
  -> no instanceof / type switch (polymorphism for the viva)
  isResolved() -> whole card grey, stays visible
```

**Failure handling in `AlertListenerThread`:**

| Exception | Status line reason | Then |
|---|---|---|
| `ConnectException` | `server not running` | wait, retry |
| `EOFException` | `server closed the connection` (also happens when maxClients is reached: the server accepts, then closes) | wait, retry |
| `InvalidClassException`, `ClassNotFoundException` | `class mismatch - rebuild all programs (...)` (v7 §75) | wait, retry |
| any other `IOException` | `connection lost (...)` | wait, retry |
| `InterruptedException` during the wait | — | restore the interrupt flag, leave the loop |

Repeated identical reasons are logged to the console only once, so the terminal isn't flooded every 3 s.

**Shutdown:** closing the window → `listener.shutdown()` sets `running = false` (volatile), calls `interrupt()` (wakes the sleep) and closes the current socket (unblocks `readObject()`). The frame uses `EXIT_ON_CLOSE`, so the process ends after that.

**Layout details:**
- Cards sit at the top of a `Scrollable` panel that follows the viewport width. Long messages wrap (`JTextArea`, word wrap, read-only) and there is no horizontal scroll. Cards are never stretched vertically.
- The message `JTextArea` uses a `DefaultCaret` with `NEVER_UPDATE`. Without it, `setText()` scrolls the caret into view and the list jumped away from the newest card during a burst (found and fixed during validation, see §5).
- Counter labels use `Severity.getColor()`; the card stripe and type label use `Alert.getColor()` (ADD green, CHG blue, DEL red), the same colours as the Admin.

## 4. New decisions (HANDOFF §3, need team approval)

| # | Decision | Why |
|---|---|---|
| D18 | Counters: one per severity counting **open** (not resolved) alerts, plus **Resolved** and **Total**; recomputed from all cards on every update | v7 §39 doesn't say what is counted. Resolving an alert visibly moves it from its severity counter to Resolved. |
| D19 | One `received instanceof Alert` check after `readObject()`; anything else is logged and skipped | `readObject()` returns `Object`. This checks what came off the network, not which subclass it is; the client never tests for `RecordAddedAlert` etc. |
| D20 | An updated card stays where it is (only new ids go to the top); no card limit, no Clear button | Not specified in v7; nothing was added beyond the spec. |

**New public API** (all additive, in `CONTRACT_SNAPSHOT.txt`):

```
dbmonitor.client.ClientMain            main(String[])
dbmonitor.client.AlertListenerThread   (String host, int port, int reconnectMs, DashboardFrame); run(); shutdown()
dbmonitor.client.DashboardFrame        (String host, int port); showAlert(Alert); showConnecting();
                                       showConnected(); showReconnecting(String reason, int reconnectMs)
dbmonitor.client.AlertCard             (Alert); update(Alert); getAlert()
```

## 5. Validation

Done on Builder B's Windows 11 PC, JDK 26.0.1, on 1 Oct 2026. **This PC has no Connector/J jar in `lib\`, so the real server and MySQL could not run.** The dashboard was therefore tested against a throwaway stand-in server (kept in the session scratchpad, not in the repo). The stand-in uses the same wire behaviour as `ClientHandler`: accept → `new ObjectOutputStream` → `flush()` → for each alert `writeObject`, `reset`, `flush`. Its alerts come from `AlertFactory.create(...)`.

### 5.1 Static checks

| Check | Result |
|---|---|
| `.\build.cmd` | ✅ BUILD OK |
| `javac --release 8 -encoding US-ASCII` on `common/` + `client/` (strict Java 8 API and syntax) | ✅ OK |
| ASCII-only sources (`-encoding US-ASCII` plus a grep for non-ASCII bytes in all changed code/scripts) | ✅ none |
| `git diff --check` (whitespace errors) | ✅ clean |
| No `printStackTrace`, no empty `catch`, no `Thread.stop()` in `client/` | ✅ |
| No `ObjectOutputStream` on the client (one-way protocol, v7 §67.7) | ✅ (only mentioned in a comment) |
| Listener never touches Swing: every `frame.*` call is inside a `Runnable` passed to `SwingUtilities.invokeLater` | ✅ (grep: 4 invokeLater blocks, nothing else) |
| `instanceof` in `client/` | only the D19 wire check |
| Protected packages unchanged vs `origin/main` | ✅ empty diff |
| `CONTRACT_SNAPSHOT.txt` matches the current build (`javap` diff) | ✅ identical |
| Branch state | `builder-b` = `origin/builder-b` = `origin/main` = `1e8e4f0`, so it merges as a fast-forward with no conflicts |

### 5.2 Run-time checks (real `DashboardFrame` + `AlertListenerThread`, stand-in server)

| HANDOFF §6 "Done when" | Result | Evidence |
|---|---|---|
| Reconnects if the server starts later | ✅ | Started with no server → orange "Reconnecting ... server not running"; stand-in started → "[INFO] connected", green "Connected to localhost:5050" |
| Cards appear by themselves | ✅ | 4 alerts (ADD/LOW, CHG/CRITICAL, DEL/HIGH, CHG/MEDIUM) → 4 cards, correct icons, colours, severity, `#id`, status, source, time |
| A repeated id updates the existing card | ✅ | Alert #2 sent twice → still one card |
| RESOLVED greys the same card, no duplicate | ✅ | #3 re-sent as RESOLVED → same card grey, label RESOLVED, still visible |
| Counters stay correct | ✅ | After 4 + duplicate + resolve + burst of 20: Critical 1, High 20, Medium 1, Low 1, Resolved 1, Total 24 (expected values) |
| A server failure doesn't erase the cards | ✅ | Stand-in stopped → "server closed the connection" → "server not running"; all 24 cards kept |
| A burst doesn't freeze the window | ✅ | 20 alerts in one go → all shown at once; the view stays on the newest card (scroll position 0) |
| (extra) Reconnects after a server restart | ✅ | Stand-in restarted → connected again automatically, cards kept |
| (extra) Long messages wrap, no truncation | ✅ | ~230-char, two-rule message shown in full over 3 lines |
| (extra) Clean shutdown | ✅ | `shutdown()` → "[INFO] AlertListenerThread: stopped", thread not alive after `join(3000)` |
| (extra) Two dashboards at once | ✅ | Two `run client` windows both connected and both received the same alerts |

### 5.3 Bug found and fixed during validation

- **Symptom:** after a burst, the card list had scrolled down by itself (scroll position 55 px instead of 0), so the newest card at the top was partly hidden.
- **Cause:** Swing's default text caret scrolls itself into view after `JTextArea.setText()`.
- **Fix:** `AlertCard` gives the message area a `DefaultCaret` with `NEVER_UPDATE`.
- **Re-test:** scroll position 0 at every checkpoint (before connect, first cards, after resolve, after burst, server down, reconnected).

### 5.4 Not tested yet

1. **The real chain** Workbench/Employee edit → trigger → `ChangeDetectorThread` → `PollerThread` → `BroadcastServer` → dashboard, with MySQL and Connector/J. This needs a PC with the jar (Builder B's own PC once the jar is in `lib\`, or Builder A's).
2. `.\run.cmd checks` (41 / 24 / 26 / 19). Not run because there is no DB here. Nothing those checks cover was changed.
3. The real window-close path (`EXIT_ON_CLOSE` after `shutdown()`). `shutdown()` itself is tested; only clicking the X wasn't automated.
4. `max clients` behaviour with the real server (the listener treats the server's close as `EOFException` → reconnect; reasoned from the code, not run).
5. macOS/Linux (`build.sh`, `run.sh client`). The edit is the same one-line pattern as the existing entries.

**Note for the live test:** on first start Windows may show a Firewall prompt for Java, because the server listens on all interfaces. *Cancel* is fine, since everything runs on localhost.

## 6. Verdict

| Question | Verdict |
|---|---|
| **Can it be pushed to `origin/builder-b`?** | ✅ **YES.** It builds, compiles under strict Java 8 rules, follows the v7 §68 rules, touches no protected package, changes no existing contract, and passed every check that can run without MySQL. One bug found during validation was fixed and re-tested. |
| **Can it be merged into `main`?** | ⏳ **Not yet.** HANDOFF §8 says `main` holds verified code only. Merge after: (a) the §6 "Done when" list is run with the real server + MySQL (steps in §7), (b) a verifier runs HANDOFF §7, (c) the team approves D18–D20. The merge itself will be a fast-forward with no conflicts. |

Push commands (Builder B):

```
git add src/dbmonitor/client build.cmd build.sh run.cmd run.sh CONTRACT_SNAPSHOT.txt README.md HANDOFF.md docs/REVIEW_BUILDER_B_DASHBOARD.md
git status
git commit -m "Phase 8-9: Swing dashboard (dbmonitor.client) + run client"
git push origin builder-b
```

## 7. Live test to run before merging (Windows, MySQL running, jar in `lib\`)

1. `.\build.cmd` → BUILD OK. With the server stopped, `.\run.cmd checks` → 41 / 24 / 26 / 19, 0 failed.
2. `.\run.cmd client` first → orange Reconnecting. Then `.\run.cmd server` → green Connected within 3 s.
3. Start two more `.\run.cmd client` windows → all show Connected.
4. Workbench: `UPDATE alert_monitor.products SET price = 49 WHERE id = 3;` → within about 2 s, all dashboards show a blue `[CHG]` CRITICAL card (NEW); Critical = 1.
5. `.\run.cmd admin` → select that alert → **Resolve** → the same card turns grey/RESOLVED on every dashboard; Critical 1 → 0, Resolved 0 → 1, Total unchanged.
6. `UPDATE alert_monitor.products SET stock = 0 WHERE id > 0;` → about 20 cards, the window stays responsive, Total = number of cards.
7. Ctrl+C the server → dashboards turn orange and keep their cards; restart the server → green again by themselves.
8. Close one dashboard, make another edit → the others still receive it.
9. `UPDATE alert_monitor.products SET price = 0 WHERE id = 4;` → R1 + R2 message wraps in full.

Then set the Phase 8–9 rows in HANDOFF §2 to ✅.

## 8. Paste this into Builder A's Claude session

```
STATE UPDATE (1 Oct 2026, Builder B, branch builder-b):
- Phase 8-9 built: package dbmonitor.client = ClientMain, AlertListenerThread, DashboardFrame, AlertCard.
  Run with `.\run.cmd client` (Swing). `run dashboard` is still test/ConsoleDashboard.
- build.cmd/build.sh compile src/dbmonitor/client; run.cmd/run.sh have `client`.
- No changes to common/, rules/, db/, server/, admin/, employee/, test/, sql/, config/.
  CONTRACT_SNAPSHOT.txt regenerated: only the 4 client classes were added.
- New public API: AlertListenerThread(String host,int port,int reconnectMs,DashboardFrame) + run() + shutdown();
  DashboardFrame(String host,int port) + showAlert(Alert) / showConnecting() / showConnected() /
  showReconnecting(String,int); AlertCard(Alert) + update(Alert) + getAlert().
- Pending team approval: D18 counters = open alerts per severity + Resolved + Total, recomputed from all
  cards; D19 one `instanceof Alert` wire check after readObject(); D20 updated card stays in place,
  no card limit.
- Verified against a stand-in server only (no Connector/J on that PC). Live MySQL test (review doc §7)
  and the HANDOFF §7 verifier run are still needed before merging builder-b into main.
- Next after merge: Phase 10 full integration / README 3-minute demo with `run client`.
```

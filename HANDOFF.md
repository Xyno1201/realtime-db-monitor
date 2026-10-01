# HANDOFF — Real-Time Database Monitor & Alert System

> **For any Claude (or other AI) picking up this project:** read this file together with `REALTIME_DB_MONITOR_MASTER_CONTEXT_v7.md` (the master context, "v7") before touching code.
> - **v7 holds the original requirements. §0 below (the v8 delta) replaces parts of v7.** Where §0 and v7 disagree, **§0 wins**: it is the team's later decision (30 Sep).
> - This file holds the **current state**: what exists, what has been verified, decisions not yet in v7, and the next tasks.
> - Any other conflict between this file, v7 and the code: report it to the human. Do not resolve it silently.
> - **For exact signatures, use `CONTRACT_SNAPSHOT.txt`.** Never rename anything to make it "cleaner".
>
> **Updated:** 1 Oct 2026, Builder B session 1: Swing dashboard (`dbmonitor.client`) built, live test pending. Every builder updates §2 and §9 at the end of each session.

---

## 0. v8 delta — what changed on 30 Sep / 1 Oct (supersedes the listed v7 sections)

**New concept.** v7's "admin types an alert" is replaced by **real change detection**. The system watches a real company table, `products`. A change made anywhere (MySQL Workbench or the app) is recorded by MySQL triggers, checked against rules from a text file, and turned into an alert that is pushed to the dashboards. The team chose this so the demo shows a system that really works, not one built only for marks.

| Area | v7 said | v8 (now) |
|---|---|---|
| Where alerts come from | Admin "Create alert" form | **Only from rules** evaluated on recorded data changes. The create form is **removed**. |
| Alert types (v7 D2) | CPU / TRANSACTION_FAILURE / SERVICE_UNAVAILABLE | **RECORD_ADDED / RECORD_CHANGED / RECORD_DELETED** (`RecordAddedAlert`, `RecordChangedAlert`, `RecordDeletedAlert`). The abstract `Alert` API is **unchanged**. |
| Triggers | out of scope | **In scope**: 3 AFTER triggers per monitored table write to `data_changes` + `data_change_values`. Honest viva wording: *trigger-based change capture + polling*, not log-based CDC. |
| New tables | `alerts` only | + `data_changes`, `data_change_values`, `alert_broadcast_log`; + monitored `products`. **Everything is created by one script, `sql/setup.sql`, run as root** (error 1419 otherwise). It drops and rebuilds the database each time. |
| Severity | typed by the admin | decided by **rules**. One change → at most **one** alert, with the **highest** severity of all matching rules. The message lists every matched rule. |
| Rules | — | **Hard-coded for the products table** in `src/dbmonitor/rules/ProductRules.java` (decided 1 Oct; an editable rules file with hot reload was built on 30 Sep, then dropped as too ambitious for now). Each rule is an object of one of 7 `Rule` subclasses (inserted, deleted, changed, drop_pct, rise_pct, threshold, delta). To change a rule: edit the line, rebuild, restart the server. |
| Server threads | Poller + TCP server | + **`ChangeDetectorThread`** (reads unprocessed changes → rules → alert). The PollerThread/broadcast part is unchanged, plus logging to `alert_broadcast_log`. |
| DB connections (v7 D5) | AlertDAO owns the one connection | **Each DAO owns its own connection** via `ManagedConnection` (isValid(2) + reconnect). The detector's transaction must never share a connection with the poller. |
| Admin | create / resolve / purge / delete | **Operator console, 4 tabs**: Alerts (Resolve/Purge/Delete), Changes, Rules (read-only), Broadcast Log. |
| Employee app | — | **New separate program** (`dbmonitor.employee`, `run employee`), added 1 Oct: the product catalogue staff use (search, sort, Add / Save / Delete = full JDBC CRUD). It knows nothing about alerts; its edits are monitored like Workbench edits. |
| Employee / approval GUI | — | **Not built.** Discussed and parked; maybe later if time allows. |

**Rules R1–R10** (agreed with the team, `ProductRules.java`):

| Rule | Fires when | Severity |
|---|---|---|
| R1 | price set to 0 or below | CRITICAL |
| R2 | price drops ≥ 50% | CRITICAL |
| R3 | price drops 20–50% | HIGH |
| R4 | price rises ≥ 100% | MEDIUM |
| R5 | product deleted | HIGH |
| R6 | stock falls to 0 | HIGH |
| R7 | stock falls below 10 | MEDIUM |
| R8 | stock changes by more than 500 in one edit | MEDIUM |
| R9 | new product added | LOW |
| R10 | name or category changed | LOW |

Threshold rules (R1, R6, R7) fire only when the value **crosses** the line: stock 5 → 3 doesn't alert again. Overlaps are intended: price → 0 matches R1 + R2, and stock → 0 matches R6 + R7. Either way, it's one alert.

---

## 1. Team model (changed 29 Sep — replaces the M1–M5 split in v7 §59/§83)

| Role | Who | Does |
|---|---|---|
| **Builder A** | team member 1 (Claude Pro) | Writes code. Built Phases 1–7 and the v8 change detection, rules and operator console. |
| **Builder B** | team member 2 (Claude Pro) | Writes code. **Next: Phases 8–9 (Swing dashboard)**, see §6. |
| **Verifiers V1–V3** | other three members | After each major step, run the checks in §7, review against v7 + §0, report issues, and ideate (presentation, viva, demo polish). |

**Everyone must still be able to explain every module in the viva.**

---

## 2. Status

| Phase | What | Status | Evidence |
|---|---|---|---|
| 1 | MySQL setup, schema, config, `AppConfig` | ✅ done | `Phase12Check` Parts A + C (tables, index, v8 tables, 3 triggers) |
| 2 | `Alert` + 3 record subclasses, enums, factory, exception | ✅ done (v8 types) | `Phase12Check` Part B |
| 3 | `AlertDAO` (+ broadcast log), `ChangeDAO`, `ProductDAO`, `ManagedConnection` | ✅ done | `DaoCheck` 24/24, `DetectionCheck` 19/19 |
| v8 | Triggers on `products`; rules engine (`dbmonitor.rules`); `ChangeDetectorThread` | ✅ done | `RuleCheck` 26/26 (no DB); `DetectionCheck` 19/19 (app CRUD → trigger → rule → alert, no double processing); live test: Workbench-style root edits → R2, R5, R6+R7, R8, R9, R10 alerts on the dashboards; `setup.sql` repairs a broken setup (wrong password, junk table) and is safe to re-run |
| 4 | Admin operator console (4 tabs) + Employee app (separate window), SwingWorker, auto-refresh | ✅ done | Driven by simulated clicks: Employee search + save → CRITICAL alert, add → R9, delete → R5, invalid price dialog, sort by price; Admin Resolve → RESOLVED broadcast. Screenshots in `docs/screenshots/` |
| 5–7 | `PollerThread`, `BroadcastServer`, `ClientHandler` | ✅ done (unchanged since v7, + broadcast log) | 2 console dashboards, reconnect, port-in-use |
| 8 | Dashboard networking (`AlertListenerThread`, `ClientMain`) | 🟡 built (Builder B), **live MySQL test pending** | Tested against a stand-in server that writes exactly like `ClientHandler`: started before the server → *Reconnecting*, then connects; server stopped → *Reconnecting*, cards kept; server restarted → reconnects; `shutdown()` ends the thread. `--release 8` compile OK |
| 9 | Dashboard GUI (`DashboardFrame`, `AlertCard`, counters) | 🟡 built (Builder B), **live MySQL test pending** | Same stand-in run: 4 alert types/severities → 4 cards; duplicate id → same card; RESOLVED → same card grey; 255-char message wraps; burst of 20 → 24 cards, counters Critical 1 / High 20 / Medium 1 / Low 1 / Resolved 1 / Total 24; the view stays on the newest card (scroll 0) through burst, server stop and reconnect |
| 10 | Full integration + demo script (README "3-minute demo") | ⏳ after 8–9 | — |

**Test environment caveat:** the build sandbox tests on Linux against MySQL 8.0 using the **MariaDB Connector/J** driver, which accepts `jdbc:mysql:` URLs. With that driver, Phase12Check Part C reports BLOCKED, so it was verified separately (41/41). **The team must re-run `.\run.cmd checks` with the real MySQL Connector/J 8.x on Windows** (task V-0 in §7). Builder A's Windows PC passed the v7 checks (36/36) before v8.

---

## 3. Decisions made while building (need team approval, then add to v7 via §60)

| # | Decision | Why |
|---|---|---|
| D1 | Root package `dbmonitor` → `.common`, `.db`, `.rules`, `.server`, `.admin`, (`.client` next) | Bare top-level names clash easily. |
| D2 | ~~CPU / TRANSACTION_FAILURE / SERVICE_UNAVAILABLE~~ → **RECORD_ADDED / RECORD_CHANGED / RECORD_DELETED** (v8) | The alert type now says what happened to the data. |
| D3 | `Alert` is immutable: getters, no setters | One Alert object is serialized by several handler threads at once. |
| D4 | No `CHECK` constraints in SQL | Validation lives in Java. It also lets Workbench save "bad" data, which the monitor then catches (the R1 demo). |
| D5 | ~~AlertDAO owns the process's connection~~ → **each DAO owns one connection** (`ManagedConnection`) (v8) | ChangeDAO uses a transaction (`setAutoCommit(false)`), which must not mix with the poller's statements. |
| D6 | Poller and detector retry a failed DB call after one interval | v7 §69.3 defines no retry key. |
| D7 | `BroadcastServer.start()` binds the port synchronously and throws `IOException` | Port conflicts are reported immediately. |
| D8 | Admin: the timer refresh reports DB errors in the status line only; button actions also show a dialog | A dialog every 3 s would make the window unusable. |
| D9 | Test harnesses live in `test/` | v7 §84.3: throwaway harnesses. |
| D10 | `build.cmd` / `run.cmd` and `build.sh` / `run.sh` | One-command demos. No Maven/Gradle. |
| D11 | `alert_broadcast_log`: the server logs every push (alert values copied, so the log survives Purge) | "This is where the pushed messages go": a visible audit trail. |
| D12 | Generic change capture: `data_changes` (one row per change) + `data_change_values` (one row per column: old/new as text) | Generic tables: another table could be added later with its own triggers + rules. Today only `products` is monitored. |
| D13 | Alert creation + marking the change processed happen in **one JDBC transaction**, guarded by `processed_at IS NULL` | Each change becomes at most one alert, even after a crash or retry. |
| D14 | Rules are created in Java (`ProductRules.create()`) as objects of subclasses of the abstract `Rule` (1 Oct: replaces the rules file) | Simpler to explain and test; still shows inheritance + polymorphism (`RuleSet.evaluate` calls `rule.evaluate` with no `instanceof`). |
| D15 | One setup script `sql/setup.sql` (run as root) creates everything and starts from scratch each time; `reset-demo.sql` only resets data | Fewer setup mistakes; a broken setup is fixed by running one file again. |
| D16 | The update trigger skips updates that change no watched column (`<=>` null-safe compare) | Workbench "Apply" with no real change must not create noise. |
| D17 | `ProductDAO` validates (no negative price or stock, max 2 decimals, lengths); SQL does not | See D4: the app is strict, and the database isn't. |
| D18 | Dashboard counters: one per severity counting **open** (not resolved) alerts, plus **Resolved** and **Total**; recomputed from all cards on every update | v7 §39 does not say what is counted. Resolving an alert visibly moves it from its severity to Resolved. |
| D19 | `AlertListenerThread` checks `received instanceof Alert` once after `readObject()`; anything else is logged and skipped | `readObject()` returns `Object`. This is a wire check, not a type switch: no code in the client tests for a subclass. |
| D20 | An updated card stays where it is (only new ids go to the top); no card limit, no Clear button | Not specified in v7; nothing was added beyond the spec. |

---

## 4. Files

```
config/app.properties                  v7 §69.3 keys
sql/setup.sql                          EVERYTHING: database, user, 4 monitor tables, products + 20 rows,
                                       3 triggers (ROOT; drops and rebuilds alert_monitor)
sql/reset-demo.sql                     clean demo state (data only)
src/dbmonitor/common/                  FROZEN once D1-D4 approved - change only via v7 §60
  AppConfig Severity AlertStatus InvalidAlertException Alert
  RecordAddedAlert RecordChangedAlert RecordDeletedAlert AlertFactory
src/dbmonitor/rules/                   ProductRules (R1-R10)  Rule (abstract) + 7 subclasses
                                       ChangeType FieldValue DataChange DetectionResult RuleSet
src/dbmonitor/db/                      ManagedConnection AlertDAO BroadcastLogEntry ChangeDAO
                                       Product ProductDAO InvalidProductException  (all DAO methods synchronized)
src/dbmonitor/server/                  ServerMain ChangeDetectorThread PollerThread BroadcastServer ClientHandler
src/dbmonitor/admin/                   AdminMain AdminFrame DbTask Renderers + a Panel/TableModel per tab
src/dbmonitor/employee/                EmployeeMain EmployeeFrame ProductTableModel EmployeeTask
src/dbmonitor/client/                  ClientMain AlertListenerThread DashboardFrame AlertCard  (Swing dashboard, run client)
test/                                  Phase12Check DaoCheck RuleCheck DetectionCheck ConsoleDashboard
CONTRACT_SNAPSHOT.txt                  javap output of every class - the exact API
```

**How the pieces connect (for the viva):**

```
Workbench / Employee app ─UPDATE─> products ──trigger──> data_changes + data_change_values
                                                                   │  (every 2 s)
ChangeDetectorThread ── RuleSet.evaluate ── ProductRules R1-R10 ──┘
        │ one transaction: INSERT alerts + mark change processed
        v
alerts ──(every 2 s)── PollerThread ──> BroadcastServer ──TCP──> dashboards
                            └──> alert_broadcast_log
Admin (4 tabs) and Employee app read/write MySQL only - never talk to the server.
```

## 5. How to run

See `README.md` (fresh install, demo). In short: `.\build.cmd`, then in separate terminals `.\run.cmd server`, `.\run.cmd admin`, `.\run.cmd employee`, `.\run.cmd dashboard`. Self-tests: `.\run.cmd checks`, with the server stopped.

---

## 6. NEXT TASK — Builder B: Phases 8–9, the Swing dashboard

**Still valid after v8:** the dashboard receives `Alert` objects, and the abstract `Alert` API didn't change. Only the concrete types did (RECORD_ADDED / CHANGED / DELETED instead of CPU etc.). A dashboard written against `Alert` needs no change.

**Goal (v7 §35–39, §49, §76, §80 M5):** a dashboard window that connects to the server, shows each alert as a coloured card, updates the same card when the same id arrives again (e.g. RESOLVED → grey), keeps the counters correct, and shows Connected / Reconnecting.

**Create, in package `dbmonitor.client`** (the class names are fixed by v7 §6.3 and §76):

| Class | Responsibility |
|---|---|
| `ClientMain` | `AppConfig.load(DEFAULT_PATH)`; if the file is missing, use `AppConfig.defaults()` with a `[WARN]`. Then start the frame on the EDT and start the listener. |
| `AlertListenerThread` | Loop: `new Socket(host, port)` → `new ObjectInputStream(...)` → `readObject()` → `SwingUtilities.invokeLater(...)`. On failure, show Reconnecting and sleep `getClientReconnectMs()`. Fresh socket and stream on every attempt. `volatile running` + `interrupt()`. **Never touches Swing directly.** Copy the pattern from `test/ConsoleDashboard.java`. |
| `DashboardFrame` | EDT only. Connection status label. Counter strip computed from an `EnumMap<Severity,Integer>`, **recomputed from all cards on every update** (v7 §39). `HashMap<Integer, AlertCard>` keyed by alert id: a new id creates a card at the top, and a known id updates the existing card (v7 §28). |
| `AlertCard` | Shows `getIconText()` (`[ADD]`/`[CHG]`/`[DEL]`), `getTypeLabel()`, severity (`getSeverity().getColor()`), `getDisplayMessage()`, `getSource()` (e.g. `products #3`), `getFormattedCreatedAt()`, `getStatus()`. Accent colour `getColor()`. **Grey when `isResolved()`**, and it stays visible. No `instanceof` or type switch: that is the polymorphism the viva needs to see. |

**Rules that bite:**
- The client has **no** `ObjectOutputStream`. It only reads.
- `ClassNotFoundException` or `InvalidClassException` means a stale build (e.g. an old `out/` with CpuAlert). Treat it as a connection failure and reconnect (v7 §75).
- Messages can be up to 255 characters and may list several rules (`[R2] ...; [R6] ...`). Wrap the text; don't truncate it silently.
- Admin deletes of alerts are not pushed, and late-joining dashboards don't get old alerts. Both are accepted limitations (v7 §52).
- Java 8 syntax and ASCII-only sources (v7 §68).

**Also update:** add `src\dbmonitor\client\*.java` to `build.cmd` and `build.sh`, add `run.cmd client` / `run.sh client`, regenerate `CONTRACT_SNAPSHOT.txt` (§7), and update §2 and §9 of this file.

**Done when (v7 §80 M5):**
- It reconnects if the server starts later.
- Cards appear by themselves after a Workbench edit.
- A repeated id updates the existing card, and RESOLVED greys the same card with no duplicate.
- The counters stay correct.
- A server failure doesn't erase the cards.
- A burst (e.g. `UPDATE products SET stock = 0 WHERE id > 0;` = about 20 alerts at once) doesn't freeze the window.

**Suggested first prompt for Builder B's Claude:**
> Attached: `REALTIME_DB_MONITOR_MASTER_CONTEXT_v7.md`, `HANDOFF.md`, `CONTRACT_SNAPSHOT.txt`, all of `src/dbmonitor/common/`, and `test/ConsoleDashboard.java`. HANDOFF §0 (v8 delta) overrides v7 where they differ. Follow the v7 §68 rules block. Implement HANDOFF §6: package `dbmonitor.client` with `ClientMain`, `AlertListenerThread`, `DashboardFrame` and `AlertCard`. Before writing any code, list your assumptions and any conflicts. Do not modify `common/`, `rules/`, `db/`, `server/`, `admin/` or `employee/`. Afterwards, give complete files, the build/run steps, and the v7 §80 M5 test procedure.

---

## 7. Verification checklist (Verifiers V1–V3, after every major step)

**V-0 (now, once per laptop):** follow `TEAMMATE_SETUP.md`. Expect `.\run.cmd checks` → Phase12Check 41, DaoCheck 24, RuleCheck 26, DetectionCheck 19 passed, **0 failed**. Report any FAIL with the exact line.

**Every step:**
1. `.\build.cmd` builds with no errors, on the lab JDK as well if possible.
2. `.\run.cmd checks` shows 0 failed, with the server stopped.
3. Run the README "3-minute demo". Each step behaves as described.
4. Code review against v7 + §0:
   - `PreparedStatement` + try-with-resources everywhere; ChangeDAO's transaction commits or rolls back on every path.
   - The poller order as v7 §71 (broadcast before the guarded update).
   - No Swing access off the EDT, and all Admin DB work in `DbTask` (SwingWorker).
   - No empty catch blocks and no `printStackTrace()`.
   - Java 8 syntax only, ASCII-only sources.
   - No new dependencies.
5. Contract check: regenerate the snapshot and diff it with the committed one. Any change to `common/` must have been announced.
   - Windows: after `.\build.cmd`, run `javap -cp out dbmonitor.common.Alert` (and similarly for the class that changed).
   - Mac/Linux: `javap -cp out $(cd out && find dbmonitor -name "*.class" ! -name '*$*' | sed 's|\.class$||;s|/|.|g' | sort)`
6. Write findings in §9 as: severity, file, what, how to reproduce.

**Failure demos, verify before the final demo:**
- MySQL stopped: the Admin shows an error and stays usable; the server logs warnings and recovers.
- Server stopped while Workbench edits continue: the changes wait (*waiting for the server...*) and are processed when it restarts.
- A second server started: clear port error.
- A dashboard closed: the others keep receiving.
- The server restarted: dashboards reconnect.

---

## 8. Sharing progress between the two builders

- **One private GitHub repo** is the single copy. `main` = verified code only. Each builder has a branch: `builder-a`, `builder-b`. A builder pushes to their own branch; Builder A merges it into `main` (a Pull Request on GitHub) after a verifier has run §7.
- Daily routine (start: `git pull origin main` into your branch; save: `git add .` → `git status` → `git commit -m "..."` → `git push`) is in `TEAMMATE_SETUP.md` Step 11.
- Not in the repo: `out/` (build output) and `lib/*.jar` (each person downloads Connector/J). `.gitattributes` keeps `.cmd` files CRLF and `.sh` files LF on every machine.
- **Start each Claude session fresh** with this bundle: v7 + `HANDOFF.md` + `CONTRACT_SNAPSHOT.txt` + the files being worked on + `common/`.
- **At the end of a session,** the builder asks Claude to update §2, §3 (if new decisions were made) and §9, then commits.
- **Never work on the same file at the same time** as the other builder. The split is by package: Builder B works in `dbmonitor.client` only for now.

---

## 9. Session log

| Date | Who | What changed | Open issues |
|---|---|---|---|
| 29 Sep | Builder A | Phases 1–7 built and tested. README, HANDOFF, build/run scripts, test harnesses. | Re-run checks with the real Connector/J on Windows (V-0). Faculty question: rubric Java-concepts lines add up to 7, not 8. |
| 30 Sep | Builder A | Broadcast log (D11), TEAMMATE_SETUP.md. **v8 concept** (§0): triggers on `products`, rules engine + rules file with hot reload, ChangeDetectorThread, connection per DAO, Admin operator console with Products CRUD (create form removed), new alert types. New checks RuleCheck and DetectionCheck. | D1–D17 need team approval. V-0 on Windows with v8. Builder B: dashboard (§6). Employee/approval GUI parked. GitHub repo still to create. |
| 1 Oct | Builder A | Rules **hard-coded** in `ProductRules.java` (rules file, RuleLoader, `rules.file` key and hot reload removed). The 4 SQL scripts merged into one `sql/setup.sql` (fresh rebuild, resets the alertapp password, ends with a SETUP OK check). Checks updated: 41 / 24 / 26 / 19. Fresh full zip. | V-0 again on Windows from the fresh zip. Builder B: dashboard (§6). GitHub repo still to create. |
| 1 Oct | Builder A | Products CRUD moved out of the Admin into a **separate Employee app** (`dbmonitor.employee`: search, sortable table, Add / Save / Delete; `run employee`). Admin now has 4 tabs. Screenshots retaken. Checks unchanged (41 / 24 / 26 / 19). | Optional later: record the employee's own name on each change (today the app shows as alertapp@localhost). |
| 1 Oct | Builder A | Repo prepared for GitHub: `.gitignore` (out/, lib/*.jar), `.gitattributes` (line endings), branches `main` / `builder-a` / `builder-b`. TEAMMATE_SETUP Steps 5 and 11 (clone, branch, daily git routine). | Builder B: start §6 on branch `builder-b`. |
| 1 Oct | Builder B | **Swing dashboard** (§6), package `dbmonitor.client`: `ClientMain`, `AlertListenerThread`, `DashboardFrame`, `AlertCard`. `run client` added to `run.cmd` / `run.sh`; `client\*.java` added to `build.cmd` / `build.sh`; `run dashboard` stays the text test dashboard. README updated (run list, demo steps 1 and 5, failure demo). `CONTRACT_SNAPSHOT.txt` regenerated (only the 4 client classes added). Decisions D18–D20. Tested against a stand-in server (no MySQL on that PC); no other package touched. | Builder B: run the §6 "Done when" list with the real server + MySQL, then update the Phase 8–9 rows to ✅. D18–D20 need team approval. Windows may show a firewall prompt for Java on first server start: Cancel is fine (localhost only). |

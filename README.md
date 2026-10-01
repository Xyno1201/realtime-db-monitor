# Real-Time Database Monitor & Alert System

A 2nd-year Java mini-project. The system **watches a real company table** (`products`) in MySQL:

1. Someone changes the data: directly in **MySQL Workbench**, or through the **Employee app** (the product catalogue staff use).
2. **MySQL triggers** record every change (who, what, old value → new value) in `data_changes`.
3. The **server** checks each change against the **product rules R1–R10**. If a rule matches, it creates an **alert** with a severity.
4. The server pushes the alert over TCP to every connected **dashboard**, and logs each push in `alert_broadcast_log`.
5. The **Admin (operator console)** shows the alerts, every recorded change, the rules and the push history.

- **Requirements:** `REALTIME_DB_MONITOR_MASTER_CONTEXT_v7.md`. **What changed since v7 is in `HANDOFF.md` §0, and §0 wins.**
- **Current progress, decisions and next tasks:** **`HANDOFF.md`**.
- **Setting up a brand-new laptop (installing Java, MySQL, etc.):** **`TEAMMATE_SETUP.md`**.

## Fresh install from this zip (do this if anything was set up wrong before)

1. **Delete your old project folder completely**, first saving your Connector/J jar from its `lib\`. Then extract this zip, e.g. to `C:\projects\realtime-db-monitor`.
2. Put the **MySQL Connector/J 8.x** jar into `lib\` (see `lib/README.txt`). Make sure it's the only jar there.
3. **MySQL Workbench** → connect to *Local instance* **as root** → *File → Open SQL Script* → `sql\setup.sql` → click ⚡.
   The last result must say **SETUP OK | 20 | 3 | 0**.
   This script deletes and rebuilds the whole `alert_monitor` database, and it resets the `alertapp` user's password. Any old mistakes are wiped.
4. In the terminal, in the project folder: `.\build.cmd`, then `.\run.cmd checks` (with the server **not** running).
   Expected, all with **0 failed**: `41 passed`, `24 passed`, `26 passed`, `19 passed`.

## What works now

| Part | What you can see |
|---|---|
| Change capture | Any INSERT / UPDATE / DELETE on `products`, from anywhere, is recorded with the MySQL user who made it. An UPDATE that changes nothing is not recorded. |
| Rules | 10 fixed rules (R1–R10) in `src/dbmonitor/rules/ProductRules.java`. One change → at most one alert, with the highest severity of all the rules it matched. |
| Server | Checks new changes every 2 s → creates alerts → pushes them to all dashboards → logs each push. |
| Employee app | A separate window, *Product Catalogue - Employee*: search, sort, **Add / Save Changes / Delete** products. It knows nothing about alerts. It's just the business app, and its edits are monitored like anyone else's. |
| Admin | A separate window with 4 tabs: **Alerts** (Resolve / Purge / Delete), **Changes** (what the triggers recorded + what the rules decided), **Rules** (read-only list), **Broadcast Log**. |
| Dashboard | `run client`: the Swing dashboard. One coloured card per alert, newest at the top; a repeated alert updates its card, and a resolved alert turns grey and stays visible. Counters per severity (open alerts) + Resolved + Total. Shows *Connected* / *Reconnecting* and reconnects by itself. Run several. (`run dashboard` is still the text-only test dashboard.) |

### The rules

| Rule | Fires when | Severity |
|---|---|---|
| R1 | price set to 0 or below | CRITICAL |
| R2 | price drops by 50% or more | CRITICAL |
| R3 | price drops by 20–50% | HIGH |
| R4 | price rises by 100% or more | MEDIUM |
| R5 | product deleted | HIGH |
| R6 | stock falls to 0 | HIGH |
| R7 | stock falls below 10 | MEDIUM |
| R8 | stock changes by more than 500 in one edit | MEDIUM |
| R9 | new product added | LOW |
| R10 | name or category changed | LOW |

Some edits match two rules, on purpose: price → 0 matches R1 + R2, and stock → 0 matches R6 + R7. Either way it's still one alert. Threshold rules (R1, R6, R7) fire only when the value *crosses* the line, so stock 5 → 3 doesn't alert again.
To change a rule, edit its line in `ProductRules.java`, run `.\build.cmd`, and restart the server.

## Build and run

Windows (cmd, or the PowerShell terminal in Antigravity/VS Code). Open a **separate terminal for each program**:

```
.\build.cmd                 build everything (rerun after any code change)
.\run.cmd checks            4 self-tests (server must be STOPPED)
.\run.cmd server            terminal 1 - leave running
.\run.cmd admin             terminal 2 - the operator console (monitoring)
.\run.cmd employee          terminal 3 - the Employee app (product catalogue)
.\run.cmd client            terminal 4+ - Swing dashboards (run several)
.\run.cmd dashboard         optional    - text-only test dashboard
```

macOS/Linux: `sh build.sh`, then `sh run.sh checks | server | admin | employee | client | dashboard`.

## 3-minute demo

Before the demo, run `sql\reset-demo.sql` in Workbench for a clean start: the 20 original products and no alerts.

1. Start `server`, `admin`, `employee` and two or three `client`s (Swing dashboards).
2. **In Workbench**, make "unauthorised" edits, one at a time:
   ```sql
   UPDATE alert_monitor.products SET price = 49 WHERE id = 3;     -- R2: price -99%      -> CRITICAL
   UPDATE alert_monitor.products SET stock = 0  WHERE id = 5;     -- R6+R7: out of stock -> HIGH
   DELETE FROM alert_monitor.products WHERE id = 20;              -- R5: deleted         -> HIGH
   UPDATE alert_monitor.products SET price = -10 WHERE id = 8;    -- R1+R2: negative     -> CRITICAL
   ```
   Within about 2 s, each one appears on every dashboard and in the Admin's **Alerts** tab, marked *by root@localhost*.
3. Open the **Changes** tab. Every edit is listed with old → new values, who made it, and the outcome (`ALERT #n (SEVERITY)` or `No rule matched`).
4. In the **Employee app**, type `coffee` in Search, select *Coffee Beans*, set the price to `500`, and click **Save Changes**. The employee just sees "saved", but the Admin and the dashboards get a CRITICAL alert, this time *by alertapp@localhost* (the Employee app). Now try a price of `-5`: the app refuses it. Workbench didn't refuse `-10`, and that's exactly why the monitor exists.
5. Select an alert and click **Resolve**. On every dashboard the same card turns grey (RESOLVED, no new card) and the counters change, and the **Broadcast Log** tab shows both pushes.
6. Optional failure demos:
   - Stop MySQL (Windows: Services → MySQL80 → Stop). The Admin shows a red database error and the server logs a warning. Start MySQL again, and both recover without a restart.
   - Close the server and keep editing in Workbench. The changes wait in `data_changes` (outcome *waiting for the server...*). Start the server, and they are all processed.
   - While the server is closed, the dashboards show *Reconnecting* in orange and keep their cards. When the server is started again, they reconnect by themselves (*Connected*, green).

## Layout

```
config/app.properties                 settings (v7 section 69.3)
sql/setup.sql                         COMPLETE setup: database, user, tables, products, triggers (as ROOT)
sql/reset-demo.sql                    clean demo state (keeps the setup)
src/dbmonitor/common/                 Alert + 3 subclasses (RecordAdded/Changed/Deleted), enums, factory, AppConfig
src/dbmonitor/rules/                  ProductRules (R1-R10), Rule + 7 subclasses, RuleSet, DataChange
src/dbmonitor/db/                     AlertDAO, ChangeDAO, ProductDAO, ManagedConnection
src/dbmonitor/server/                 ServerMain, ChangeDetectorThread, PollerThread, BroadcastServer, ClientHandler
src/dbmonitor/admin/                  AdminMain, AdminFrame + one panel per tab (operator console)
src/dbmonitor/employee/               EmployeeMain, EmployeeFrame, ProductTableModel, EmployeeTask (Employee app)
test/                                 Phase12Check, DaoCheck, RuleCheck, DetectionCheck, ConsoleDashboard
docs/screenshots/                     the Admin's 4 tabs and the Employee app
CONTRACT_SNAPSHOT.txt                 real method signatures - attach to AI prompts
HANDOFF.md                            status, decisions, next tasks, team workflow
```

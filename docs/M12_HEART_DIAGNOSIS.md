# Saved-world Heart diagnosis: 23 October 2026

The operator accepts spread but reports an unmineable Heart after removing the nearby sculk. The accepted rule remains **60 occupied ACTIVE seconds below the living threshold**, including when zero connected cells remain. The operator reconfirmed this after reviewing the recorded wait.

The saved journal reaches813/1200ticks (40.65seconds), then resets after loss of a verifiable loaded root. An earlier reset follows a reconnected cell/topology change. There is no evidence of a stuck-empty topology or a completed wait being rejected. The continuous clock itself proves the disconnected bank was not blocking verification during that interval. Restart deliberately starts a new full wait; this rule is unchanged.

`EcologyHeartEngineTest.removingLastConnectedCellAllowsEmptyNetworkToCompleteDefaultWait` removes the last owned sculk, replays that genuinely empty indexed network, verifies tick1199 versus1200 and durably ends it. Focused Heart engine tests pass (`:campaigns:quiet-below:test --tests *EcologyHeartEngineTest`,19s).

`python scripts/heart_repro_smoke.py` passes on an isolated player-loaded clone of the exact saved review: `../reports/heart-repro-20261023T173517597389Z/report.json`. Nine native checks observe9/19/29/39/49/59seconds, then a vulnerable brown lens, actual Survival iron-pickaxe mining, the local cryptic cue and root removal. Root inspected the screenshot. No tuning, fast-forward or forced chunks; authenticated source properties are unchanged. The complete original source world was compared to its pre-test backup afterward: every file matches SHA256. The final driver also fingerprints every source-world file including the ecology journal and asserts terminal client checks explicitly.

Earlier test-only failures were an overlong offline account name and an incomplete status-message matcher. They were corrected before the successful run; failed runtime copies were retired after saved-stop/path/lock checks, reports retained. No gameplay defect fix is claimed by this reproduction.

The operator separately requests a new last-stand response: visible/audible pulses, one bounded final spread attempt and one directly summoned Warden, with a configurable **10 active-minute persisted cooldown** gating the whole phase across reconnects/restarts. The independent60second vulnerability clock remains. This new behaviour is a separate implementation slice, not a workaround for a failed timer. Region/menu and automatic special-block-registration simplification follow it sequentially.

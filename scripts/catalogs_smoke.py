"""Compatibility entry point for the superseding Region/Location subtype fixture.

The old separate Area changes/Departures screen probes were retired in dev58.
This invokes the current native suite; it does not claim those former screens exist.
"""
from subtypes_smoke import run

if __name__ == "__main__":
    run()

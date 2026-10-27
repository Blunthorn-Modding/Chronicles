"""Compatibility entrypoint for the consolidated Region/Location native fixture."""
from __future__ import annotations

def run() -> None:
    import subtypes_smoke
    print("Catalog editor smoke moved to subtypes_smoke.py; running the consolidated Region/Location checks.", flush=True)
    subtypes_smoke.run()

if __name__ == "__main__":
    run()

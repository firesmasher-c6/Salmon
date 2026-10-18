# Salmon optimization guide

## Focus areas
- World generation cost: regenerate offline with Chunky and trim interaction distance.
- Entity volume: chunk caps at spawn time, cleanup of aged non-important mobs.
- Plugin and scheduler overhead: metrics opt-in, watchdog removal only when needed.
- Redstone: alternate engine tradeoffs with documentation.
- Villager and pathfinding cost: proximity-aware AI throttling.

## Fixed constraints
- No silent breakage of item sorters or locator-like redstone behavior.
- No reverting features without comments.
- Safe defaults preferred over dramatic behavior changes.

# Cherry-pick plan: Pufferfork / Pufferfish ideas (no copy)

Pufferfork tracks upstream (Paper) and keeps Pufferfish feature patches.

## Notable Pufferfork/Pufferfish optimizations
1. Dynamic Activation of Brain (DAB) — distance-based brain/pathfinder throttling.
2. Optimize mob spawning — async spawn counting/backoff improvements.
3. Activation-range special-case improvements (Paper#12764 port) — preserve relevant mobs near players.
4. Async chunk/pre-generation model — use Chunky, control max active chunk loads/gens.
5. Paper/Pufferfish config profile tuning (activation, tracking, spawning, redstone, collisions).

## Salmon 26.2 execution order
- Tracking/activation knobs first
- Async safe-tuning defaults next
- Villager-style AI throttling only as a real runtime feature
- Always keep neutral defaults and test impact first

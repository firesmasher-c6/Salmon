# Salmon — Landing / Documentation-Only Branch

This branch never contains server source code. It exists for documentation, project links, branch index, and release history.

## Project overview
Salmon is a performance-focused Minecraft server fork in the Paper ecosystem, aimed at large SMP deployments: throughput, memory, chunk/thread efficiency, and operator-friendly optimization defaults.

Design goals:
- Safe defaults, fast upgrades
- Opt-in behavior-changing features
- Clear tradeoff documentation
- Stable + dev branch model
- Small, reviewable PRs

## Branches
- `landing` — docs only (this branch)
- `ver/1.21.11` — stable 1.21.11 line (legacy)
- `release/1.21.11-3r-009` — stable release line (1.21.11, currently unsupported)
- `dev/26.2` — active development on Paper 26.2; newer performance work will happen here

## Release history
See `COMMIT_HISTORY.md` plus individual GitHub release pages for verified downloads.

## Discord server (coming soon)
A public Discord server is being prepared for support, release announcements, and testing coordination.
It is not open yet — once ready, invite link will be added here.

## Optimization roadmap
- Chunk load/generate pacing defaults and safer world pre-generation guidance
- Entity activation and villager AI proximity throttling
- Improved spawn rules and configuration self-healing
- Tracking range & object churn reductions
- Continued compatibility with upstream Paper/Pufferfish features

## Scope & policy notes
- Prefer pre-generating worlds (Chunky) to avoid TPS drops during live generation.
- Degraded world features (frozen ticks) are always documented and opt-in.
- No disruptive bulk features that break vanilla-like item sorters/smart mobs by default.

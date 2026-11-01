# Minecraft1.21.4 ChestMenu API regression (334)

Only two public API declarations are recorded, not a replacement Minecraft implementation.
`manifest.json` pins the MCP1.21.4 mirror commit, path, full source SHA-256 and excerpt hash.
The author's actual333 `compileJava` diagnostic independently confirms that `oneRow`
accepts only `(int, Inventory)`. To bind an existing nine-slot container, use the public
five-argument constructor with `MenuType.GENERIC_9x1` and row count1.

`audit/critical_state_test.py` compiles the production native outer-transaction test method
against these signatures, then executes it with explicitly modeled MC/API boundaries.
It also compiles the exact old333 caller and requires a `oneRow` argument error.
This expected compiler rejection is separate from behavioral mutation kills, for which
compiler failure never counts as success. The positive probe checks container identity,
row count and both merge directions, so a private empty factory container cannot stand
in for the actual insertion destination.

This does not prove complete Mojmap/NeoForge compatibility, real Mixin application or
native GameTest execution. The runtime fixtures and full Gradle build remain separate.

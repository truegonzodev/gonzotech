# Pinned payload API boundaries (330)

Exact source bytes + SHA-256/length/repository/ref in `manifest.json`.
NeoForge **1.21.4 branch**, commit f4b4a9655362b103b95769258ed6586597d8b7f4;
FancyModLoader **1.21.4 branch**, c5f2a36970f1f0a691590acd394932305ab3d6b9.
These are not proof of the exact installed NeoForge 21.4.157 binary.

`payload_ingress_test.py` compiles **whole unmodified** upstream PayloadRegistrar,
HandlerThread, MainThreadPayloadHandler, ModConfigEvent with minimal boundary doubles.
This exercises default MAIN wrapping and copy-returning executesOn(NETWORK), rather
than a handwritten registrar emulation. IPayloadContext, configuration, lifecycle
patches/extensions are API/source pins, not a real network/bootstrap test.

ConfigValue cachedValue is not volatile: production only reads the published
primitive snapshot on Netty; mod config events publish it. Accepted Fence uses the
same enqueueWork ordering; no tick buffering. Executor/Connection/menu/codec/event
boundaries are explicit doubles; no native Netty, vanilla ordering or mixin proof.

Upstream source retains copyright/SPDX headers; LGPL-2.1 text in LICENSE-NeoForge.txt.

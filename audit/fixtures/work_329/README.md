# Exact own-code 329 negative controls

Frozen whole files from commit 9c2d9b3 (Git object/hash/bytes in manifest).
`plotnick_budget_test.py` inserts the unchanged old processCalculation method:
it compiles but fails the two-global-start limit assertions.
`payload_ingress_test.py` uses the old Plotnick payload, old Fence registration,
and old default-MAIN Plotnick registration separately: each compiles and
assertion-fails. Compile errors do not count as mutation kills.

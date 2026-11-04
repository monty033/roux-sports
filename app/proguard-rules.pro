# R8 rules for the release build. Only rules the build demonstrably needs live here.
#
# At present that is none. :app:minifyReleaseWithR8 succeeds with no missing-class or missing-rule diagnostics,
# because every library that needs keep rules ships them as consumer rules, which AGP merges automatically:
#   - kotlinx-serialization keeps @Serializable classes' Companion and serializer() (the DTOs in :data);
#   - WorkManager keeps Worker class names and constructors (GameWatchWorker and DailyReconcileWorker are built
#     by name in RouxWorkerFactory, and their names are persisted in the work database across app updates);
#   - Ktor, OkHttp, Okio, coroutines and Coil add their own -dontwarn / keep rules for optional classes.
# The app also wires its components explicitly (HttpClient(engine), KtorNetworkFetcherFactory, SvgDecoder.Factory),
# so there is no ServiceLoader or reflection lookup of its own for R8 to break.
#
# Add a rule here only after R8 reports a missing class or a release-only failure is reproduced, and give it a
# one-line comment saying what it protects and the failure it prevents. Never use a blanket -keep class ** { *; }.
